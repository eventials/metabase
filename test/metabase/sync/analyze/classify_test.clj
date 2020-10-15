(ns metabase.sync.analyze.classify-test
  (:require [clojure.test :refer :all]
            [metabase.models
             [database :refer [Database]]
             [field :as field :refer [Field]]
             [field-values :as field-values]
             [table :refer [Table]]]
            [metabase.sync.analyze.classify :as classify]
            [metabase.sync.interface :as i]
            [metabase.util :as u]
            [toucan.util.test :as tt]))

(deftest fields-to-classify-test
  (testing "Finds current fingerprinted versions that are not analyzed"
    (tt/with-temp* [Table [table]
                    Field [_ {:table_id            (u/get-id table)
                              :name                "expected"
                              :description         "Current fingerprint, not analyzed"
                              :fingerprint_version i/latest-fingerprint-version
                              :last_analyzed       nil}]
                    Field [_ {:table_id            (u/get-id table)
                              :name                "not expected 1"
                              :description         "Current fingerprint, already analzed"
                              :fingerprint_version i/latest-fingerprint-version
                              :last_analyzed       #t "2017-08-09"}]
                    Field [_ {:table_id            (u/get-id table)
                              :name                "not expected 2"
                              :description         "Old fingerprint, not analyzed"
                              :fingerprint_version (dec i/latest-fingerprint-version)
                              :last_analyzed       nil}]
                    Field [_ {:table_id            (u/get-id table)
                              :name                "not expected 3"
                              :description         "Old fingerprint, already analzed"
                              :fingerprint_version (dec i/latest-fingerprint-version)
                              :last_analyzed       #t "2017-08-09"}]]
      (is (= ["expected"]
             (for [field (#'classify/fields-to-classify table)]
               (:name field)))))))

(deftest classify-fields-for-db!-test
  (testing "We classify decimal fields that have specially handled NaN values"
    (tt/with-temp* [Database [db]
                    Table    [table {:db_id (u/get-id db)}]
                    Field    [field {:table_id            (u/get-id table)
                                     :name                "Income"
                                     :base_type           :type/Float
                                     :special_type        nil
                                     :fingerprint_version i/latest-fingerprint-version
                                     :fingerprint         {:type   {:type/Number {:min "NaN"
                                                                                  :max "NaN"
                                                                                  :avg "NaN"}}
                                                           :global {:distinct-count 3}}
                                     :last_analyzed       nil}]]
      (is (nil? (:special_type (Field (u/get-id field)))))
      (classify/classify-fields-for-db! db [table] (constantly nil))
      (is (= :type/Income (:special_type (Field (u/get-id field)))))))
  (testing "We can classify decimal fields that have specially handled infinity values"
    (tt/with-temp* [Database [db]
                    Table    [table {:db_id (u/get-id db)}]
                    Field    [field {:table_id            (u/get-id table)
                                     :name                "Income"
                                     :base_type           :type/Float
                                     :special_type        nil
                                     :fingerprint_version i/latest-fingerprint-version
                                     :fingerprint         {:type   {:type/Number {:min "-Infinity"
                                                                                  :max "Infinity"
                                                                                  :avg "Infinity"}}
                                                           :global {:distinct-count 3}}
                                     :last_analyzed       nil}]]
      (is (nil? (:special_type (Field (u/get-id field)))))
      (classify/classify-fields-for-db! db [table] (constantly nil))
      (is (= :type/Income (:special_type (Field (u/get-id field))))))))

(defn- ->field [field]
  (field/map->FieldInstance
    (merge {:fingerprint_version i/latest-fingerprint-version
            :special_type        nil}
           field)))

(deftest run-classifiers-test
  (testing "Fields marked state are not overridden"
    (let [field (->field {:name "state", :base_type :type/Text, :special_type :type/State})]
      (is (= :type/State (:special_type (classify/run-classifiers field nil))))))
  (testing "Fields with few values are marked as category and list"
    (let [field      (->field {:name "state", :base_type :type/Text})
          classified (classify/run-classifiers field {:global
                                                      {:distinct-count
                                                       (dec field-values/category-cardinality-threshold)
                                                       :nil% 0.3}})]
      (is (= {:has_field_values :auto-list, :special_type :type/Category}
             (select-keys classified [:has_field_values :special_type])))))
  (testing "Earlier classifiers prevent later classifiers"
    (let [field       (->field {:name "site_url" :base_type :type/Text})
          fingerprint {:global {:distinct-count 4
                                :nil%           0}}
          classified  (classify/run-classifiers field fingerprint)]
      (is (= {:has_field_values :auto-list, :special_type :type/URL}
             (select-keys classified [:has_field_values :special_type]))))))

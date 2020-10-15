(ns metabase.query-processor.streaming.xlsx-test
  (:require [clojure.test :refer :all]
            [java-time :as t]
            [metabase.query-processor.streaming.xlsx :as streaming.xlsx :refer [excel-format-value]])
  (:import java.time.Instant))

(deftest excel-format-value-test
  (testing "LocalDate"
    (isa? Instant (excel-format-value (t/local-date 2020 06 01))))

  (testing "LocalDateTime"
    (isa? Instant (excel-format-value (t/local-date-time 2020 06 01 23 55 00))))

  (testing "OffsetDateTime"
    (isa? Instant (excel-format-value (t/offset-date-time 2019 01))))

  (testing "ZonedDateTime"
    (isa? Instant (excel-format-value (t/with-zone (t/zoned-date-time 2020 01 05) "UTC"))))

  (testing "SQL Timestamp"
    (isa? Instant (excel-format-value (java.sql.Timestamp. (System/currentTimeMillis)))))

  (testing "a String"
    (is (= "Fooo"
           (excel-format-value "Fooo"))))

  (testing "a nil"
    (is (= nil
           (excel-format-value nil))))

  (testing "an Integer"
    (is (= (Integer. 1234)
           (excel-format-value (Integer. 1234))))))

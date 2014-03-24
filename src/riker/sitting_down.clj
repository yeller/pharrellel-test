(ns riker.sitting-down
  (:require [clojure.test :as t]
            [clojure.stacktrace :as stack])
  (:import java.util.concurrent.LinkedBlockingQueue
           java.util.AbstractQueue))

(def ^{:dynamic true} *result-queue* nil)


(defmacro with-test-out-str
  "Evaluates exprs in a context in which *out* and *test-out* are bound to a
  fresh StringWriter.  Returns the string created by any nested printing
  calls."
  {:added "1.0"}
  [& body]
  `(let [s# (new java.io.StringWriter)]
     (binding [t/*test-out* s#
               *out* s#]
       ~@body
       (str s#))))

(defmacro with-test-out [& body]
  `(let [x# (with-test-out-str ~@body)]
     (.put *result-queue* x#)))

;; monkeypatch all of these, they're done dumbly

(defmethod t/report :default [m]
  (with-test-out (prn m)))

(defmethod t/report :pass [m]
  (with-test-out (t/inc-report-counter :pass)))

(defmethod t/report :fail [m]
  (with-test-out
    (t/inc-report-counter :fail)
    (println "\nFAIL in" (t/testing-vars-str m))
    (when-let [message (:message m)] (println message))
    (println "expected:" (pr-str (:expected m)))
    (println "  actual:" (pr-str (:actual m)))))

(defmethod t/report :error [m]
  (with-test-out
   (t/inc-report-counter :error)
   (println "\nERROR in" (t/testing-vars-str m))
   (when-let [message (:message m)] (println message))
   (println "expected:" (pr-str (:expected m)))
   (print "  actual: ")
   (let [actual (:actual m)]
     (if (instance? Throwable actual)
       (stack/print-cause-trace actual t/*stack-trace-depth*)
       (prn actual)))))

(defmethod t/report :summary [m]
  (with-test-out
   (println "\nRan" (:test m) "tests containing"
            (+ (:pass m) (:fail m) (:error m)) "assertions.")
   (println (:fail m) "failures," (:error m) "errors.")))

(defmethod t/report :begin-test-ns [m]
  (with-test-out
   (println "\nTesting" (ns-name (:ns m)))))

;; Ignore these message types:
(defmethod t/report :end-test-ns [m])
(defmethod t/report :begin-test-var [m])
(defmethod t/report :end-test-var [m])

(def ^{:dynamic true} *parallelism* 4)

(defn gather-tests-from-ns [^AbstractQueue queue n]
  (let [once-fixture-fn (t/join-fixtures (::once-fixtures (meta n)))
        each-fixture-fn (t/join-fixtures (::each-fixtures (meta n)))]
    (doseq [v (vals (ns-interns n))]
      (when (:test (meta v))
        (.put queue [v each-fixture-fn once-fixture-fn])))))


(defn test-var [^AbstractQueue result-queue v]
  (when-let [t (:test (meta v))]
    (try (t)
      (catch Throwable e
        (t/do-report
          {:type :error, :message "Uncaught exception, not in assertion."
           :expected nil, :actual e})))))

(defn run-worker [results tests-to-run worker-id finished]
  (future
    (try
      (binding [*result-queue* results]
        (loop []
          (if-let [[tvar each-fixture once-fixture] (.poll tests-to-run)]
            (do
              (once-fixture
                (fn []
                  (each-fixture
                    #(test-var results tvar))))
              (println "finished running " tvar)
              (recur))
            (do
              (println "finished on " worker-id)
              (deliver (nth finished worker-id) 1)))))
      (catch Throwable e
        (.printStackTrace e))
      (finally (deliver (nth finished worker-id) 1)))))

(defn run-gathered-tests [^AbstractQueue tests-to-run]
  (let [results (LinkedBlockingQueue.)
        finished (into [] (map (fn [_] (promise)) (range *parallelism*)))]
    (dotimes [worker-id *parallelism*]
      (run-worker results tests-to-run worker-id finished))
    (doseq [n finished]
      (deref n))
    (doseq [r (iterator-seq (.iterator results))]
      (println r))))

(defn gather-tests [ns-re]
  (let [queue (LinkedBlockingQueue.)]
    (doseq [n (filter #(re-matches ns-re (name (ns-name %))) (all-ns))]
      (gather-tests-from-ns queue n))
    queue))

(defn run-tests [ns-re]
  (-> (gather-tests ns-re)
    run-gathered-tests))

(defn -main [& args]
  (run-tests #"riker.*"))

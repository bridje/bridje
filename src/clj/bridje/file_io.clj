(ns bridje.file-io
  (:require [clojure.java.io :as io]
            [clojure.string :as s]))

(defprotocol FileIO
  (slurp-source-file [_ ns-sym])
  (slurp-compiled-file [_ ns-sym file-type])
  (spit-compiled-file [_ ns-sym file-type content]))

(defn ->file-path [ns-sym file-type]
  (str (-> (name ns-sym)
           (s/split #"\.")
           (->> (s/join "/")))
       "."
       (name file-type)))

(defn ->io [{:keys [compile-path] :or {compile-path (io/file "bridje-stuff" "node")}}]
  (let [->compiled-file (fn [ns-sym file-type]
                          (io/file compile-path (->file-path ns-sym file-type)))]

    (reify FileIO
      (slurp-source-file [_ ns-sym]
        (when-let [source-file (io/resource (->file-path ns-sym :brj))]
          (slurp source-file)))

      (slurp-compiled-file [_ ns-sym file-type]
        (let [compiled-file (->compiled-file ns-sym file-type)]
          (when (.exists compiled-file)
            (slurp compiled-file))))

      (spit-compiled-file [_ ns-sym file-type content]
        (spit (doto (->compiled-file ns-sym file-type)
                (io/make-parents))
              content)))))

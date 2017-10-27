(ns bridje.forms
  (:require [bridje.runtime :as rt]
            [clojure.string :as s]
            [clojure.walk :as w]))

(do
  (def form-types
    #{:string :bool
      :int :float :big-int :big-float
      :symbol :namespaced-symbol
      :record :list :set :vector})

  (def form-adt-kw
    (into {}
          (map (fn [form-type]
                 (let [[_ fst snd] (re-matches #"([a-z]+)(-[a-z]+)*" (name form-type))]
                   [form-type (keyword (name 'bridje.kernel.forms)
                                       (str (s/capitalize fst)
                                            (when snd
                                              (s/capitalize (subs snd 1)))
                                            "Form"))])))
          form-types))

  (def adt-form-type
    (comp (into {} (map (fn [[k v]] [v k])) form-adt-kw)
          :adt-type)))

(defn wrap-forms [obj]
  (w/postwalk (fn [obj]
                (if (and (map? obj) (:form-type obj))
                  (rt/->ADT (form-adt-kw (:form-type obj))
                            (dissoc obj :form-type))
                  obj))
              obj))

(defn unwrap-forms [obj]
  (w/postwalk (fn [obj]
                (if-let [form-type (adt-form-type obj)]
                  (merge (:params obj)
                         {:form-type form-type})

                  obj))
              obj))

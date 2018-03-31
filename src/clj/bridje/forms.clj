(ns bridje.forms
  (:require [bridje.runtime :as rt]
            [clojure.string :as s]
            [clojure.walk :as w]))

(defn adt-syms [type variants]
  (into {}
        (map (fn [variant]
               (let [[_ fst snd] (re-matches #"([a-z]+)(-[a-z]+)*" (name variant))]
                 [variant (symbol (name 'bridje.kernel.forms)
                                  (str (s/capitalize fst)
                                       (when snd
                                         (s/capitalize (subs snd 1)))
                                       (s/capitalize (name type))))])))
        variants))

(defn adt-types->kws [adt-type-syms]
  (into {} (map (fn [[k v]] [v k])) adt-type-syms))

(defmacro defadt [type variants]
  (let [type-adt-syms (symbol (str type "-adt-syms"))
        adt-type->kw (symbol (format "%s-adt-type->kw" (name type)))
        type-kw (keyword (str type "-type"))]
    `(let [variants# ~variants]
       (def ~type-adt-syms
         (adt-syms '~type variants#))

       (def ~adt-type->kw
         (adt-types->kws ~type-adt-syms))

       (defn ~(symbol (format "wrap-%ss" (name type))) [obj#]
         (w/postwalk (fn [obj#]
                       (if (and (map? obj#) (~type-kw obj#))
                         (rt/->ADT (~type-adt-syms (~type-kw obj#))
                                   (dissoc obj# ~type-kw))
                         obj#))
                     obj#))

       (defn ~(symbol (format "unwrap-%ss" (name type))) [obj#]
         (w/postwalk (fn [obj#]
                       (if-let [variant# (~adt-type->kw (:adt-type obj#))]
                         (merge (:params obj#)
                                {~type-kw variant#})

                         obj#))
                     obj#)))))

(defadt form
  #{:string :bool
    :int :float :big-int :big-float
    :symbol
    :record :list :set :vector})

(defadt expr
  #{:string :bool
    :int :float :big-int :big-float
    :local :global :clj-var
    :vector :set :record
    :if :let :fn :case
    :loop :recur})

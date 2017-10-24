(ns bridje.quoter
  (:require [bridje.analyser :as analyser]
            [bridje.util :as u]))

(defn quoted-form [form-type params]
  {:form-type :list
   :forms [{:form-type :namespaced-symbol
            :ns 'bridje.forms
            :sym (symbol (str "->" (name (u/form-adt-kw form-type))))}
           {:form-type :record
            :forms (->> params
                        (into [] (mapcat (fn [[k v]]
                                           [{:form-type :symbol
                                             :sym k}
                                            v]))))}]})

(defn expand-syntax-quotes [form {:keys [current-ns] :as env}]
  (letfn [(syntax-quote-form [{:keys [form-type forms] :as form} {:keys [splice?]}]
            (let [quoted-form (case form-type
                                :syntax-quote (-> (:form form)
                                                  (syntax-quote-form {:splice? false})
                                                  (syntax-quote-form {:splice? false}))

                                :unquote (:form form)

                                :unquote-splicing (if splice?
                                                    (:form form)
                                                    (throw (ex-info "unquote-splicing used outside of collection" {:form form})))

                                :quote {:form-type :quote
                                        :form (syntax-quote-form (:form form) {:splice? false})}

                                (:string :bool :int :float :big-int :big-float) {:form-type :quote,
                                                                                 :form form}

                                (:symbol :namespaced-symbol) (let [{:keys [ns sym]} (or (analyser/env-resolve form :vars env)
                                                                                        {:ns (or (:ns form) current-ns),
                                                                                         :sym (:sym form)})]
                                                               (quoted-form :namespaced-symbol
                                                                            {'ns {:form-type :quote,
                                                                                  :form {:form-type :symbol
                                                                                         :sym ns}}
                                                                             'sym {:form-type :quote,
                                                                                   :form {:form-type :symbol
                                                                                          :sym sym}}}))

                                (quoted-form form-type
                                             {'forms (let [splice? (some #(= :unquote-splicing (:form-type %)) forms)
                                                           inner-forms {:form-type :vector
                                                                        :forms (mapv #(syntax-quote-form % {:splice? splice?}) forms)}]
                                                       (if splice?
                                                         {:form-type :list
                                                          :forms [{:form-type :namespaced-symbol
                                                                   :ns 'bridje.forms
                                                                   :sym 'concat}
                                                                  inner-forms]}

                                                         inner-forms))}))]

              (if (and splice? (not= form-type :unquote-splicing))
                {:form-type :vector
                 :forms [quoted-form]}

                quoted-form)))

          (expand-sq* [{:keys [form-type] :as form}]
            (case form-type
              (:string :bool :int :big-int :float :big-float :symbol :namespaced-symbol) form
              (:vector :set :list :record) (update form :forms #(mapv expand-sq* %))
              :quote (update form :form expand-sq*)

              :syntax-quote (syntax-quote-form (:form form) {:splice? false})
              :unquote (throw (ex-info "'unquote' outside of 'syntax-quote'" {:form form}))
              :unquote-splicing (throw (ex-info "'unquote-splicing' outside of 'syntax-quote'" {:form form}))))]

    (expand-sq* form)))

(defn expand-quotes [{:keys [form-type] :as form}]
  (letfn [(sym-form [sym]
            {:form-type :list
             :forms [{:form-type :namespaced-symbol
                      :ns 'bridje.forms
                      :sym '->Symbol}
                     {:form-type :string,
                      :string (name sym)}]})

          (quote-form [{:keys [form-type] :as form}]
            (if (= form-type :quote)
              (quote-form (quote-form (:form form)))

              (quoted-form form-type
                           (case form-type
                             :string {'string form}
                             :bool {'bool form}

                             (:int :float :big-int :big-float) {'number form}

                             :symbol {'sym (sym-form (:sym form))}

                             :namespaced-symbol {'ns (sym-form (:ns form))
                                                 'sym (sym-form (:sym form))}

                             (:list :vector :set :record) {'forms {:form-type :vector,
                                                                   :forms (mapv quote-form (:forms form))}}))))]
    (case form-type
      (:string :bool :int :big-int :float :big-float :symbol :namespaced-symbol) form
      (:vector :set :list :record) (update form :forms #(mapv expand-quotes %))
      :quote (quote-form (:form form)))))

(comment
  (let [env {:global-env {'bridje.forms {:vars {'->VectorForm {:value {}}
                                                '->IntForm {:value {}}
                                                '->ListForm {:value {}}
                                                '->StringForm {:value {}}
                                                '->RecordForm {:value {}}
                                                '->SymbolForm {:value {}}
                                                '->NamespacedSymbolForm {:value {}}}}}}]
    (-> (expand-normal-quotes (first (bridje.reader/read-forms "['[1 '''1]]")))
        (analyser/analyse env)
        (bridje.emitter/emit-value-expr env))))

(comment
  (let [env {:current-ns 'bridje.foo
             :global-env {'bridje.forms {:vars {'->VectorForm {:value {}}
                                                '->IntForm {:value {}}
                                                '->ListForm {:value {}}
                                                '->QuotedForm {:value {}}
                                                '->StringForm {:value {}}
                                                '->RecordForm {:value {}}
                                                '->SymbolForm {:value {}}
                                                '->Symbol {:value {}}
                                                '->NamespacedSymbolForm {:value {}}}}}}]
    (-> (first (bridje.reader/read-forms "''foo"))
        (expand-syntax-quotes env)
        (expand-quotes)
        (analyser/analyse env)
        (bridje.emitter/emit-value-expr env))))

(comment
  (-> (first (bridje.reader/read-forms "`[1 ~@[2 3 4]]"))
      (expand-syntax-quotes {}))

  (-> (first (bridje.reader/read-forms "'x"))
      expand-quotes))

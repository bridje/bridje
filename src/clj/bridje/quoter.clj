(ns bridje.quoter
  (:require [clojure.string :as str]))

#_(defn expand-syntax-quotes [form {:keys [env] :as ctx}]
  (letfn [(syntax-quote-form [{:keys [form-type forms], inner-form :form, :as form} {:keys [splice?]}]
            (let [quoted-form (case form-type
                                :syntax-quote (-> inner-form
                                                  (syntax-quote-form {:splice? false})
                                                  (syntax-quote-form {:splice? false}))

                                :unquote inner-form

                                :unquote-splicing (if splice?
                                                    inner-form
                                                    (throw (ex-info "unquote-splicing used outside of collection" {:form form})))

                                :quote {:form-type :quote
                                        :form (syntax-quote-form inner-form {:splice? false})}

                                :symbol (quoted-form :symbol
                                                     [:quote [:symbol (:sym form)]]
                                                     :form {:form-type :symbol
                                                            :sym (:sym form)})

                                (:vector :set :list :record)
                                (quoted-form form-type
                                             {'forms (let [splice? (some #(= :unquote-splicing (:form-type %)) forms)
                                                           inner-forms {:form-type :vector
                                                                        :forms (mapv #(syntax-quote-form % {:splice? splice?}) forms)}]
                                                       (if splice?
                                                         {:form-type :list
                                                          :forms [{:form-type :symbol
                                                                   :sym 'concat}
                                                                  inner-forms]}

                                                         inner-forms))})

                                {:form-type :quote, :form form})]

              (if (and splice? (not= form-type :unquote-splicing))
                {:form-type :vector
                 :forms [quoted-form]}

                quoted-form)))

          (expand-sq* [{:keys [form-type] :as form}]
            (case form-type
              (:vector :set :list :record) (update form :forms #(mapv expand-sq* %))
              :quote (update form :form expand-sq*)

              :syntax-quote (syntax-quote-form (:form form) {:splice? false})
              :unquote (throw (ex-info "'unquote' outside of 'syntax-quote'" {:form form}))
              :unquote-splicing (throw (ex-info "'unquote-splicing' outside of 'syntax-quote'" {:form form}))

              form))]

    (expand-sq* form)))

(comment
  (-> (first (bridje.reader/read-forms "`[1 ~@[2 3 4]]"))
      (expand-syntax-quotes {}))

  (-> (first (bridje.reader/read-forms "'(foo 4 [2 3])"))
      expand-quotes))

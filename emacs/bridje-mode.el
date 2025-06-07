;;; bridje-mode.el --- Major mode for Bridje files -*- lexical-binding: t; -*-
;;
;; Copyright (C) 2025 James Henderson
;;
;; Author: James Henderson <james@jarohen.dev>
;; Maintainer: James Henderson <james@jarohen.dev>
;; Created: June 04, 2025
;; Modified: June 04, 2025
;; Version: 0.0.1
;; Homepage: https://github.com/bridje/bridje
;; Package-Requires: ((emacs "30.1"))
;;
;; This file is not part of GNU Emacs.
;;
;;; Commentary:
;;
;;  Description
;;
;;; Code:

(require 'treesit)
(require 'lsp)

(defvar bridje-mode--grammar-name 'bridje)

(setq treesit-extra-load-path '("~/src/james/bridje/tree-sitter/build/lib/native/linux/x64"))

;;;###autoload
(define-derived-mode bridje-ts-mode prog-mode "Bridje[TS]"
  "Bridje mode (Tree-sitter powered)."
  :group 'bridje

  (when (treesit-ready-p 'bridje)
    (treesit-parser-create 'bridje)
    (setq-local treesit-thing-settings
                '((bridje (sexp (or "nil" "boolean" "int" "string" "symbol" "keyword"
                                    "list" "map" "set" "vector")))))

    (setq-local treesit-font-lock-feature-list '(( default )))

    (setq-local treesit-font-lock-settings
                (treesit-font-lock-rules
                 :language 'bridje
                 :feature 'default
                 '((nil) @font-lock-constant-face
                   (boolean) @font-lock-constant-face
                   (int) @font-lock-constant-face
                   (float) @font-lock-constant-face
                   (bigint) @font-lock-constant-face
                   (bigdec) @font-lock-constant-face
                   (string) @font-lock-string-face
                   (keyword) @font-lock-constant-face
                   ((symbol) @font-lock-keyword-face (:equal @font-lock-keyword-face "def"))
                   ((symbol) @font-lock-keyword-face (:equal @font-lock-keyword-face "let"))
                   ((symbol) @font-lock-keyword-face (:equal @font-lock-keyword-face "ns"))
                   (symbol_dot) @font-lock-variable-name-face
                   (dot_symbol) @font-lock-variable-name-face
                   (comment) @font-lock-comment-face)))

    (setq-local treesit-simple-indent-rules
                '((bridje
                   ((parent-is "list") parent 2)
                   ((parent-is "vector") parent 1)
                   ((parent-is "map") parent 1)
                   ((parent-is "set") parent 1))))

    (treesit-major-mode-setup)))

(add-to-list 'lsp-language-id-configuration '(bridje-ts-mode . "bridje-ts"))

(lsp-register-client
 (make-lsp-client
  :new-connection (lsp-stdio-connection
                   (lambda ()
                     (let ((root (lsp--suggest-project-root)))
                       (list (expand-file-name "gradlew" root) ":bridjeLsp"))))
  :activation-fn (lsp-activate-on "bridje-ts")
  :server-id 'bridje-lsp
  :major-modes '(bridje-ts-mode)))

(add-hook 'bridje-ts-mode-hook #'lsp)

(add-to-list 'auto-mode-alist '("\\.brj\\'" . bridje-ts-mode))

(defun bridje-eval-defun ()
  "Eval the top-level Bridje form at point via the LSP server."
  (interactive)
  (let ((form-text (thing-at-point 'defun t)))
    (let ((result
           (lsp-request
            "workspace/executeCommand"
            `(:command "bridje/eval-defun"
              :arguments (:uri ,(lsp--buffer-uri)
                          :code ,form-text)))))
      (message "=> %s" result))))

(provide 'bridje-mode)

;;; bridje-mode.el ends here

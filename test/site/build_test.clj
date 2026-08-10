(ns site.build-test
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.test :refer [deftest is]]
            [site.build :as site])
  (:import (java.nio.file Files)
           (java.nio.file.attribute FileAttribute)))

(defn- temporary-directory []
  (.toFile (Files/createTempDirectory "website-e2e-"
                                      (make-array FileAttribute 0))))

(defn- delete-recursively! [file]
  (when (.exists file)
    (doseq [child (.listFiles file)]
      (delete-recursively! child))
    (Files/delete (.toPath file))))

(defn- write-file! [directory relative-path content]
  (let [file (io/file directory relative-path)]
    (.mkdirs (.getParentFile file))
    (spit file content)
    file))

(deftest builds-a-complete-static-site
  (let [root       (temporary-directory)
        source-dir (io/file root "articles")
        output-dir (io/file root "public")]
    (try
      (write-file! source-dir
                   "notes/first-post.md"
                   "---\ntitle: First post\ndate: 2026-08-09\ndescription: A Markdown test post.\nauthor: Test Author\n---\n\nA **Markdown** post.\n\n```python\nprint(\"highlighted\")\n```")
      (let [{:keys [article-count]} (site/build-site! (.getPath source-dir)
                                                      (.getPath output-dir))
            markdown-page             (slurp (io/file output-dir "posts/notes/first-post/index.html"))]
        (is (= 1 article-count))
        (is (.isFile (io/file output-dir "index.html")))
        (is (.isFile (io/file output-dir "style.css")))
        (is (.isFile (io/file output-dir "site.css")))
        (is (str/includes? markdown-page "<header class=\"site-header\">"))
        (is (str/includes? markdown-page "<h1>First post</h1>"))
        (is (str/includes? markdown-page "datetime=\"2026-08-09\""))
        (is (str/includes? markdown-page "By Test Author"))
        (is (str/includes? markdown-page "<strong>Markdown</strong>"))
        (is (str/includes? markdown-page "class=\"language-python\""))
        (is (str/includes? markdown-page "highlight.min.js"))
        (is (str/includes? markdown-page "\n  <head>")))
      (finally
        (delete-recursively! root)))))

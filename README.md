# valentinmouret.com

This repository builds a static personal website. The generated `public/`
directory contains only HTML, CSS, images, and PDFs; it has no server-side or
browser-side rendering step.

## Generate the website

Install [Nix](https://nixos.org/download/) with flakes enabled. From the
repository root, generate the website with:

```sh
nix run .#build
```

The first run downloads the Clojure dependencies declared in `deps.edn`. The
generated files are written to `public/`. Commit that directory: it is the
deployable website served by the static host.

For an interactive Clojure environment, enter the development shell first:

```sh
nix develop
clojure -M:build
```

## Run the end-to-end test

The end-to-end test uses Cognitect's test runner. It builds a temporary website
from a Markdown article, then verifies the generated page, shared layout,
copied assets, and pretty-printed output.

```sh
nix develop --command clojure -M:test
```

## Format and lint the source

Format all Clojure source and tests, plus `flake.nix`, in place:

```sh
nix fmt
```

Run Clojure linting with clj-kondo and Nix linting with statix and deadnix,
without modifying files:

```sh
nix run .#lint
```

## Enable pre-commit checks

Git 2.55 or later can register a named, project-local pre-commit check without
replacing other hooks. Entering the Nix development shell configures this
checkout:

```sh
nix develop
```

Before each commit, Git runs the check without modifying files. It rejects
unformatted Clojure or Nix code, plus Clojure and Nix lint failures. Run
`nix fmt`, stage the result, and commit again when formatting fails.

## Write an article

Add a Markdown (`.md`) file below `content/articles/`.
Nested directories become nested URLs. For example,
`content/articles/notes/a-quiet-tool.md` generates
`public/posts/notes/a-quiet-tool/index.html`.

Articles can start with optional YAML-style front matter. The supported fields
are `title`, `date`, `description`, and `author`.

```md
---
title: A quiet tool
date: 2026-08-09
description: A short summary for search engines and link previews.
author: Valentin Mouret
---

Write the article in **Markdown** here.
```

Every generated article receives the same document head, site header, article
title, and metadata. Article-specific content is the only part that changes.
The root `index.html` remains a hand-written static homepage; add a link there
when you publish an article, then regenerate and commit `public/`.

## Customize the layout

The build function is `site.build/build-site!` in `src/site/build.clj`.
Its shared page layout uses Hiccup and is pretty-printed during the build.
Update `render-layout` to change the
shared article markup. Update `site.css`
for the generated-header styles, and keep the existing `style.css` for the
site-wide typography and article styling.

{
  description = "Valentin Mouret's static website";

  inputs.nixpkgs.url = "github:NixOS/nixpkgs/nixos-unstable";

  outputs =
    { nixpkgs, ... }:
    let
      systems = [
        "aarch64-darwin"
        "x86_64-darwin"
        "aarch64-linux"
        "x86_64-linux"
      ];
      forAllSystems = nixpkgs.lib.genAttrs systems;
    in
    {
      devShells = forAllSystems (
        system:
        let
          pkgs = import nixpkgs { inherit system; };
        in
        {
          default = pkgs.mkShell {
            packages = [
              pkgs.clojure
              pkgs.clj-kondo
              pkgs.cljfmt
              pkgs.deadnix
              pkgs.git
              pkgs.nixfmt
              pkgs.statix
            ];
            shellHook = ''
              if [ -d .git ]; then
                git config --local --unset-all core.hooksPath || true
                git config --local --replace-all hook.website-checks.event pre-commit
                git config --local hook.website-checks.command "nix run .#pre-commit"
              fi
            '';
          };
        }
      );

      formatter = forAllSystems (
        system:
        let
          pkgs = import nixpkgs { inherit system; };
        in
        pkgs.writeShellApplication {
          name = "format-website";
          runtimeInputs = [
            pkgs.cljfmt
            pkgs.nixfmt
          ];
          text = ''
            cljfmt fix src test
            nixfmt flake.nix
          '';
        }
      );

      apps = forAllSystems (
        system:
        let
          pkgs = import nixpkgs { inherit system; };
          build-site = pkgs.writeShellApplication {
            name = "build-site";
            runtimeInputs = [ pkgs.clojure ];
            text = ''
              exec clojure -M:build "$@"
            '';
          };
          format-website = pkgs.writeShellApplication {
            name = "format-website";
            runtimeInputs = [
              pkgs.cljfmt
              pkgs.nixfmt
            ];
            text = ''
              cljfmt fix src test
              nixfmt flake.nix
            '';
          };
          lint-website = pkgs.writeShellApplication {
            name = "lint-website";
            runtimeInputs = [
              pkgs.clj-kondo
              pkgs.deadnix
              pkgs.statix
            ];
            text = ''
              clj-kondo --lint src test
              statix check flake.nix
              deadnix --fail flake.nix
            '';
          };
          pre-commit-check = pkgs.writeShellApplication {
            name = "pre-commit-check";
            runtimeInputs = [
              pkgs.clj-kondo
              pkgs.cljfmt
              pkgs.deadnix
              pkgs.nixfmt
              pkgs.statix
            ];
            text = ''
              cljfmt check src test
              nixfmt --check flake.nix
              clj-kondo --lint src test
              statix check flake.nix
              deadnix --fail flake.nix
            '';
          };
        in
        {
          build = {
            type = "app";
            program = "${build-site}/bin/build-site";
          };
          format = {
            type = "app";
            program = "${format-website}/bin/format-website";
          };
          lint = {
            type = "app";
            program = "${lint-website}/bin/lint-website";
          };
          pre-commit = {
            type = "app";
            program = "${pre-commit-check}/bin/pre-commit-check";
          };
        }
      );
    };
}

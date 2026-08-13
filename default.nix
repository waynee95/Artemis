{ pkgs ? import <nixpkgs> { } }:

pkgs.mkShell {
  packages = with pkgs; [
    jdk25
    nodejs_24
    pnpm
    git
    docker
    docker-compose
  ];

  shellHook = ''
    export JAVA_HOME=${pkgs.jdk25}
  '';
}

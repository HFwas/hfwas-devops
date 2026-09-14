#!/bin/sh
case "$1" in
  *[Uu]sername*) printf '%s' "$HFWAS_GITLAB_USER" ;;
  *) printf '%s' "$HFWAS_GITLAB_SECRET" ;;
esac

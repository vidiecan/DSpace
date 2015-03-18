#!/bin/bash
# to be called as fillProperties.sh build.properties substituted_dspace.cfg MVN_ENV.properties
# it filters substituted_dspace.cfg into MVN_ENV.properties leaving out anything that is not in build.properties
egrep $(echo "^("$(egrep -v "(^#|^$)" $1 | cut -d= -f1) | sed -e 's/\s\+/|/g')")" $2 >$3

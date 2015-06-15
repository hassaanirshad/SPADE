#!/bin/bash
SPADE_ROOT="$( cd "$( dirname "${BASH_SOURCE[0]}" )"/../ && pwd )"
pushd ${SPADE_ROOT} > /dev/null
java -cp './build:./lib/*' spade.client.ControlClient
popd > /dev/null

#!/bin/bash

set -x 

./meta-dcos --user=root --password=letmein --meta-host=meta.galacticfog.com --meta-port=80 --environment=galacticfog/DemoWorkspace/Prod --provider=GF_Cluster


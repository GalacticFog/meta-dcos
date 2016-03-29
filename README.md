# meta-dcos
A Meta CLI that configures DCOS for multiple providers. This CLI will allow you to locate a provider for your org, workspace, and environment.  This provider will then 
be used to configure the DCOS CLI to use as a marathon provider.

#Install

###DCOS-CLI

The first requirement is to install the DCOS command line utility which can be found here : https://github.com/mesosphere/dcos-cli

Follow the directions for installing the DCOS CLI and be sure that you have added it to your path.  There is an instruction for how to add the cli to your path on the github page.

###META-DCOS

The meta-dcos will be delivered as an artifact, but if you are building this yourself then you can use the command `sbt update clean compile assembly`.

This will place the fat-jar file into the directory `target/scala_2.11/`.  Once this has been done you can use the `meta-dcos` script located in the root directory of this 
project.

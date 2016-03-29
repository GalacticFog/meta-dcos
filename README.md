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

#Execution

There is a sample script in the root directory called `meta-dcos` which will invoke the jar that contains the CLI.  If you simply run the CLI with no parameters you will 
be prompted to enter the Meta connection info.  You may also pass this connection info as paramters when invoking the CLI.

### CLI Arguments

* meta-host - This is the hostname where the meta instance is located that contains the provider information (e.g. meta.dev.galacticfog.com)
* meta-port - This is the port used to communicate with the meta instance ( e.g. 80 )
* user - This is the username for the user that will be used to communicate with meta
* password - The password for the meta user
* environment - The fully qualified resource name (FQRN) for the environment to use
* provider - The name of the provider to configure for DCOS

A one-liner version of the meta-dcos command line execution would look like this :

`./meta-dcos --user=foo --password=bar --meta-host=meta.dev.galacticfog.com --meta-port=80 --environment=pepsi/engineering/dev --provider=PepsiCo-Provider-1`

If you would like to use the menu to find the org and environment you can leave off those parameters :

`./meta-dcos --user=foo --password=bar --meta-host=meta.dev.galacticfog.com --meta-port=80`

If you would like to use the wizard for all input the minimal invokation would look like this :

`./meta-dcos`


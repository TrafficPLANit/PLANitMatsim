# PLANitMatsim

Repository allowing one to write a PLANit network to disk as a MATSIM (intermodal) network.

> This repository has been implemented by the University of Sydney for the ATRC project. The ATRC is a project lead by the Australian Urban Research Infrastructure Network (AURIN) and is supported by the Australian Research Data Commons (ARDC). AURIN and the ARDC are funded by the National Collaborative Research Infrastructure Strategy (NCRIS).  
ATRC Investment: https://doi.org/10.47486/PL104  
ATRC RAiD: https://hdl.handle.net/102.100.100/102.100.100/399880 

## Development

### Maven parent

PLANit MATSim has the following PLANit specific dependencies (See pom.xml):

* planit-parentpom
* planit-core
* planit-utils

Dependencies will be automatically downloaded from the PLANit website, (www.repository.goplanit.org)[http://repository.goplanit.org], or alternatively can be checked-out locally for local development. The shared PLANit Maven configuration can be found in planit-parent-pom which is defined as the parent pom of each PLANit repository.

> When developing on multiple PLANit projects locally, including the parent-pom; make sure you install the PLANitParentPom pom.xml before conducting a Maven build (in for example Eclipse), otherwise it resorts to the online repository rather then the local one.

### Maven deploy

Distribution management is setup via the parent pom such that Maven deploys this project to the PLANit online repository (also specified in the parent pom). To enable deployment ensure that you setup your credentials correctly in your settings.xml as otherwise the deployment will fail.

## Git Branching model

We adopt GitFlow as per https://nvie.com/posts/a-successful-git-branching-model/
# PLANitMatsim

Repository allowing one to write a PLANit network to disk as a MATSIM (intermodal) network.

> Implementation of PLANitMAtsim is partially funded by the University of Sydney and the Australian Transport Research Cloud ([ATRC](https://ardc.edu.au/project/australian-transport-research-cloud-atrc/)). ATRC is a project instigated by the Australian Research Data Cloud ([ARDC](www.ardc.edu.au)).

## Development

### Maven parent

Projects need to be built from Maven before they can be run. The common maven configuration can be found in the PLANitParentPom project which acts as the parent for this project's pom.xml.

> Make sure you install the PLANitParentPom pom.xml before conducting a maven build (in Eclipse) on this project, otherwise it cannot find the references dependencies, plugins, and other resources.

### Maven deploy

Distribution management is setup via the parent pom such that Maven deploys this project to the PLANit online repository (also specified in the parent pom). To enable deployment ensure that you setup your credentials correctly in your settings.xml as otherwise the deployment will fail.

## Git Branching model

We adopt GitFlow as per https://nvie.com/posts/a-successful-git-branching-model/
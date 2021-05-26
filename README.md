# PLANitMatsim

Repository allowing one to write a PLANit network to disk as a MATSIM (intermodal) network

## Maven parent

Projects need to be built from Maven before they can be run. The common maven configuration can be found in the PLANitParentPom project which acts as the parent for this project's pom.xml.

> Make sure you install the PLANitParentPom pom.xml before conducting a maven build (in Eclipse) on this project, otherwise it cannot find the references dependencies, plugins, and other resources.

## Git Branching model

We adopt GitFlow as per https://nvie.com/posts/a-successful-git-branching-model/
# Release Log

This project contains core code to write a MATSIM network to disk based on PLANit memory model

## 0.4.0

**Enhancements**

* [GENERAL] Add support for writing out transit schedules based on PLANit routed services and service network via intermodal writer
* #11 Add CI by running tests whenever pushing a commit
* #10 Add option to persist Stops in csv format compatible with MATSim's Matrix based Pt Router for teleported modes
* #9 Add option to restrict link speeds to maximum speed of supported/activated modes in writer
* #8 Add option to remove all existing mode mappings for network writer

**Bug fixes**

## 0.3.0

* mapping of default modes should be based on common matsim modes: car, pt. This has been added #3
* updated artifact id to conform with how this generally is setup, i.e. <application>-<subrepo> #5
* it was not possible to map pedestrian and bicycle PLANit modes to MATSim modes. This has now been added as an option #6
* update packages to conform to new domain org.goplanit.* #7

## 0.2.0

* first implementation of matsim network writer based on planit network memory model (single network layer support only)
* add LICENSE.TXT to each repository so it is clearly licensed (planit/#33)
* verify if external id's are unique when used as id mapper when writing networks (planitmatsim/#2) 


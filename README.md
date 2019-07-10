# LDAPResources Rundeck Plugin

This is a resource model plugin for Rundeck which supports reading node definitions
from an LDAP directory.

## Configuration

For the time being the plugin requires a configuration file and reads all its
configuration parameters from there

### Configuration sample

```yaml
---
user: LDAP_USER
password: LDAP_PASSWORD
url: LDAP_URL
port: 636
search_base: "sc-category=entities,ou=syscontrol,dc=cern,dc=ch"
filter: "(sc-domain=dod)"
name_attribute: sc-entity
user_attribute: sc-run-as
os_attribute: sc-os
search_attribute: sc-category
entity_node_attributes:
    - sc-state
    - sc-version
tag_attributes:
    - sc-type
    - sc-subcategory
    - sc-category
    - sc-domain
entity_subnodes:
    addresses:
        sc-category: addresses 
        objectclass: sc-address
    db-addresses: 
        sc-category: db-addresses 
        objectclass: sc-db-address
    nfs-volumes: 
        sc-category: nfs-volumes
        objectclass: sc-nfs-volume
    hosts: 
        sc-category: hosts
        objectclass: sc-host-instance
    tnsnetservices: 
        sc-category: tnsnetservices
        objectclass: sc-oracle-service
    ping-entities: 
        sc-category: db-addresses 
        objectclass: sc-ping-entity
    clusters: 
        sc-category: clusters
        objectclass: sc-wls-cluster-properties
    machines: 
        sc-category: machines
        objectclass: sc-wls-machine-properties
    servers: 
        sc-category: servers
        objectclass: sc-wls-server-properties
    tomcat-server: 
        sc-category: tomcat-server
        objectclass: sc-tomcat-server
```

## TODO

* [] Implement configuration sanitation and required configuration checks
* [] Implement proper exception management/logging
* [] Expose some of the config parameters as Plugin properties




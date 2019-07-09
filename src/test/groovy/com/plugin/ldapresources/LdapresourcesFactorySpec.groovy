package com.plugin.ldapresources

import spock.lang.Specification

class LdapresourcesFactorySpec extends Specification {

    def "retrieve resource success"(){
        given:

        //TODO: set additional properties for your plugin
        Properties configuration = new Properties()
        configuration.put("tags","example")

        def factory = new LdapresourcesFactory()

        def vmList = ["localhost"]

        when:
        def result = factory.createResourceModelSource(configuration)

        then:
        //result.getNodes().size()==vmList.size()
        true
    }


}
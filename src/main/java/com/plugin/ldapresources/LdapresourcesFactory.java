package com.plugin.ldapresources;

import com.dtolabs.rundeck.core.common.INodeSet;
import com.dtolabs.rundeck.core.common.NodeEntryImpl;
import com.dtolabs.rundeck.core.common.NodeSetImpl;
import com.dtolabs.rundeck.core.resources.ResourceModelSource;
import com.dtolabs.rundeck.core.resources.ResourceModelSourceFactory;
import com.dtolabs.rundeck.core.plugins.Plugin;
import com.dtolabs.rundeck.core.plugins.configuration.Describable;
import com.dtolabs.rundeck.core.plugins.configuration.Description;
import com.dtolabs.rundeck.core.plugins.configuration.ConfigurationException;
import com.dtolabs.rundeck.core.plugins.configuration.PropertyUtil;
import com.dtolabs.rundeck.core.resources.ResourceModelSourceException;
import com.dtolabs.rundeck.plugins.util.DescriptionBuilder;

import org.apache.directory.api.ldap.model.cursor.CursorException;
import org.apache.directory.api.ldap.model.cursor.EntryCursor;
import org.apache.directory.api.ldap.model.entry.Attribute;
import org.apache.directory.api.ldap.model.entry.Entry;
import org.apache.directory.api.ldap.model.entry.Value;
import org.apache.directory.api.ldap.model.exception.LdapException;
import org.apache.directory.api.ldap.model.message.SearchScope;
import org.apache.directory.ldap.client.api.LdapNetworkConnection;
import org.apache.log4j.Logger;
import org.yaml.snakeyaml.Yaml;

import java.io.FileInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Properties;

@Plugin(service = "ResourceModelSource", name = "ldapresources")
public class LdapresourcesFactory implements ResourceModelSourceFactory, Describable {

    public static final Logger logger = Logger.getLogger(LdapresourcesFactory.class);

    public static final String PROVIDER_NAME = "ldapresources";
    public static final String PROVIDER_TITLE = "LDAP Resource Model";
    public static final String PROVIDER_DESCRIPTION = "LDAP Resource Model ";

    /**
     * Overriding this method gives the plugin a chance to take part in building the
     * {@link com.dtolabs.rundeck.core.plugins.configuration.Description} presented
     * by this plugin. This subclass can use the {@link DescriptionBuilder} to
     * modify all aspects of the description, add or remove properties, etc.
     */
    @Override
    public Description getDescription() {
        return DescriptionBuilder.builder().name(PROVIDER_NAME).title(PROVIDER_TITLE).description(PROVIDER_DESCRIPTION)
                .property(PropertyUtil.string("ldapConfigFile", "LDAP Configuration file", "Config file.", false, null,
                        null, null, null))
                .build();
    }

    /**
     * Here is the meat of the plugin implementation, which should perform the
     * appropriate logic for your plugin.
     */
    @Override
    public ResourceModelSource createResourceModelSource(final Properties properties) throws ConfigurationException {
        final LDAPresources resource = new LDAPresources(properties);
        return resource;
    }

    class LDAPresources implements ResourceModelSource {

        private final Properties configuration;
        private final Map ldapConfig;

        public LDAPresources(Properties configuration) {
            this.configuration = configuration;
            logger.error("Reading ldapConfigFile:" + this.configuration.getProperty("ldapConfigFile"));
            this.ldapConfig = this.getConfig(configuration.getProperty("ldapConfigFile"));
        }

        private Map getConfig(String configFile) {
            try {
                InputStream inputStream;
                Yaml yaml = new Yaml();
                inputStream = new FileInputStream(configFile);
                Map data = (Map) yaml.load(inputStream);
                return data;
            } catch (Exception e) {
                System.out.println("Error processing YAML: " + e.getMessage());
                return null;
            }
        }

        @Override
        public INodeSet getNodes() throws ResourceModelSourceException {

            final NodeSetImpl nodeSet = new NodeSetImpl();

            String user = (String) this.ldapConfig.get("user");
            String password = (String) this.ldapConfig.get("password");
            String url = (String) this.ldapConfig.get("url");
            Integer port = (Integer) this.ldapConfig.get("port");
            String userdn = String.format("cn=%s,ou=users,dc=cern,dc=ch", user);
            String entities_base = (String) this.ldapConfig.get("entities_base");

            try {

                logger.error("LDAP Connection to " + url + ":" + port);
                LdapNetworkConnection connection = new LdapNetworkConnection(url, port, true);

                logger.error("Bind: " + this.ldapConfig.get("userdn"));
                connection.bind(userdn, password);

                logger.error("Searching: " + this.ldapConfig.get("search_base"));
                String filter = (String) this.ldapConfig.get("filter");
                EntryCursor entities = connection.search(entities_base, filter, SearchScope.SUBTREE, "*");

                // Main entities iteration loop
                while (entities.next()) {
                    Entry entity = entities.get();
                    logger.error("Entity:" + entity.getDn());

                    // Initialize new empty node
                    NodeEntryImpl node = new NodeEntryImpl();
                    if (null == node.getAttributes()) {
                        node.setAttributes(new HashMap<>());
                    }
                    HashMap<String, String> nodeAttributes = (HashMap<String, String>) node.getAttributes();
                    HashSet<String> tagset = new HashSet<>();

                    node.setNodename(entity.get((String) this.ldapConfig.get("name_attribute")).get().toString());
                    node.setUsername(entity.get((String) this.ldapConfig.get("user_attribute")).get().toString());

                    // node.setHostname("localhost");

                    Collection<Attribute> entityAttributes = entity.getAttributes();

                    // Process attributes
                    ArrayList<String> selected_attributes = (ArrayList<String>) this.ldapConfig.getOrDefault("entity_node_attributes", new ArrayList<String>());
                    ArrayList<String> tag_attributes = (ArrayList<String>) this.ldapConfig.getOrDefault("tag_attributes", new ArrayList<String>());
                    for (Attribute attribute : entityAttributes) {
                        logger.error("Attr: " + attribute.getId());
                        Value attrValue = attribute.get();
                        if (selected_attributes.contains(attribute.getId())) {
                            nodeAttributes.put(attribute.getId(), attribute.get().toString());
                        }
                        if (tag_attributes.contains(attribute.getId())) {
                            tagset.add(attribute.get().toString());
                        }
                    }
    
                    // Sub nodes
                    Map<String, Map<String,String>> sub_nodes = (Map<String, Map<String,String>>) this.ldapConfig.getOrDefault("entity_subnodes", Collections.emptyList());
    
                    sub_nodes.forEach((key,value) -> {
                        
                        logger.debug(String.format("%s: %s", key, value.toString()));
    
                        String sc_category = value.get("sc-category");
                        String objectclass = value.get("objectclass");
    
                        String search_base = String.format("sc-category=%s,", sc_category) + entity.getDn();
                        String search_filter = String.format("(objectClass=%s)", objectclass);
                        logger.debug("search_base=" + search_base);
                        EntryCursor subNodeEntities;
                        try {
                            subNodeEntities = connection.search(search_base, search_filter, SearchScope.SUBTREE, "*");
                            subNodeEntities.forEach((subNode) -> {
                                logger.info(key + ":" + subNode.getDn());
                            });
                        } catch (LdapException e) {
                            logger.error("Error querying entity sub-nodes");
                            logger.error(e.getMessage());
                        }
                    
                    });
                    
                    // Complete node and register to nodeSet
                    tagset.add("ldap");
                    if (! tagset.isEmpty()) node.setTags(tagset);
                    node.setAttributes(nodeAttributes);
                    nodeSet.putNode(node);
                    
                }
    
                logger.error("Total: " + total.toString());
                logger.error("Unbinding");
                connection.unBind();
    
                logger.error("Closing connection");
                connection.close();

                return nodeSet;
    
            } catch (LdapException | CursorException e) {
                logger.error("Error performing LDAP operation");
                logger.error(e.getMessage());
                return null;
            }
    
        }

    }

}
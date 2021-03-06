/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.springboot.maven;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.apache.camel.maven.packaging.MvelHelper;
import org.apache.camel.springboot.maven.model.SpringBootAutoConfigureOptionModel;
import org.apache.camel.springboot.maven.model.SpringBootModel;
import org.apache.camel.tooling.util.Strings;
import org.apache.camel.util.json.DeserializationException;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;
import org.apache.camel.util.json.Jsoner;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.mvel2.templates.TemplateRuntime;
import org.sonatype.plexus.build.incremental.BuildContext;


import static org.apache.camel.tooling.util.PackageHelper.loadText;
import static org.apache.camel.tooling.util.PackageHelper.writeText;

/**
 * For all the Camel components that has Spring Boot starter JAR, their documentation
 * .adoc files in their component directory is updated to include spring boot auto configuration options.
 */
@Mojo(name = "update-spring-boot-auto-configuration-readme", threadSafe = true)
public class UpdateSpringBootAutoConfigurationReadmeMojo extends AbstractMojo {

    /**
     * The maven project.
     */
    @Parameter(property = "project", required = true, readonly = true)
    protected MavenProject project;

    /**
     * The project build directory
     *
     */
    @Parameter(defaultValue = "${project.build.directory}")
    protected File buildDir;

    /**
     * Whether to fail the build fast if any Warnings was detected.
     */
    @Parameter
    protected Boolean failFast;

    /**
     * Whether to fail if an option has no documentation.
     */
    @Parameter
    protected Boolean failOnMissingDescription;

    /**
     * build context to check changed files and mark them for refresh (used for
     * m2e compatibility)
     */
    @Component
    private BuildContext buildContext;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        try {
            executeStarter(buildDir.getParentFile());
        } catch (Exception e) {
            throw new MojoFailureException("Error processing spring-configuration-metadata.json", e);
        }
    }

    private void executeStarter(File starter) throws Exception {
        File jsonFile = new File(buildDir, "classes/META-INF/spring-configuration-metadata.json");

        // only if there is components we should update the documentation files
        if (jsonFile.exists()) {
            getLog().debug("Processing Spring Boot auto-configuration file: " + jsonFile);
            Object js = Jsoner.deserialize(new FileReader(jsonFile));
            if (js != null) {
                String name = starter.getName();

                if (!isValidStarter(name)) {
                    return;
                }

                if (name.startsWith("camel-")) {
                    // skip camel-  and -starter in the end
                    name = name.substring(6);
                }
                boolean isStarter = false;
                if (name.endsWith("-starter")) {
                    // skip camel-  and -starter in the end
                    name = name.substring(0, name.length() - 8);
                    isStarter = true;
                }
                String componentName = name;
                getLog().debug("Camel component: " + componentName);
                File docFolder = new File(starter,"src/main/docs/");
                File docFile = new File(docFolder, isStarter ? componentName + "-starter.adoc" : componentName + ".adoc");

                List<SpringBootAutoConfigureOptionModel> models = parseSpringBootAutoConfigureModels(jsonFile, null);

                // check for missing description on options
                boolean noDescription = false;
                for (SpringBootAutoConfigureOptionModel o : models) {
                    if (Strings.isEmpty(o.getDescription())) {
                        noDescription = true;
                        getLog().warn("Option " + o.getName() + " has no description");
                    }
                }
                if (noDescription && isFailOnNoDescription()) {
                    throw new MojoExecutionException("Failed build due failOnMissingDescription=true");
                }

                String changed = templateAutoConfigurationOptions(models, componentName);
                boolean updated = updateAutoConfigureOptions(docFile, changed);
                if (updated) {
                    getLog().info("Updated doc file: " + docFile);
                } else {
                    getLog().debug("No changes to doc file: " + docFile);
                }
            }
        }
    }

    // TODO: later
    private static String asComponentName(String componentName) {
        if ("fastjson".equals(componentName)) {
            return "json-fastjson";
        } else if ("gson".equals(componentName)) {
            return "json-gson";
        } else if ("jackson".equals(componentName)) {
            return "json-jackson";
        } else if ("johnzon".equals(componentName)) {
            return "json-johnzon";
        } else if ("snakeyaml".equals(componentName)) {
            return "yaml-snakeyaml";
        } else if ("cassandraql".equals(componentName)) {
            return "cql";
        } else if ("josql".equals(componentName)) {
            return "sql";
        } else if ("juel".equals(componentName)) {
            return "el";
        } else if ("jsch".equals(componentName)) {
            return "scp";
        } else if ("printer".equals(componentName)) {
            return "lpr";
        } else if ("saxon".equals(componentName)) {
            return "xquery";
        } else if ("stringtemplate".equals(componentName)) {
            return "string-template";
        } else if ("tagsoup".equals(componentName)) {
            return "tidyMarkup";
        }
        return componentName;
    }

    private static boolean isValidStarter(String name) {
        return true;
    }

    private List<SpringBootAutoConfigureOptionModel> parseSpringBootAutoConfigureModels(File file, String include) throws IOException, DeserializationException {
        getLog().debug("Parsing Spring Boot AutoConfigureModel using include: " + include);
        List<SpringBootAutoConfigureOptionModel> answer = new ArrayList<>();

        JsonObject obj = (JsonObject) Jsoner.deserialize(new FileReader(file));

        JsonArray arr = obj.getCollection("properties");
        if (arr != null && !arr.isEmpty()) {
            arr.forEach(e -> {
                JsonObject row = (JsonObject) e;
                String name = row.getString("name");
                String javaType = row.getString("type");
                String desc = row.getStringOrDefault("description", "");
                String defaultValue = row.getStringOrDefault("defaultValue", "");

                // is the option deprecated then include that as well in the description
                String deprecated = row.getStringOrDefault("deprecated", "");
                String deprecationNote = row.getStringOrDefault("deprecationNote", "");
                if ("true".equals(deprecated)) {
                    desc = "*Deprecated* " + desc;
                    if (!Strings.isEmpty(deprecationNote)) {
                        if (!desc.endsWith(".")) {
                            desc = desc + ". Deprecation note: " + deprecationNote;
                        } else {
                            desc = desc + " Deprecation note: " + deprecationNote;
                        }
                    }
                }

                // skip this special option and also if not matching the filter
                boolean skip = name.endsWith("customizer.enabled") || include != null && !name.contains("." + include + ".");
                if (!skip) {
                    SpringBootAutoConfigureOptionModel model = new SpringBootAutoConfigureOptionModel();
                    model.setName(name);
                    model.setJavaType(javaType);
                    model.setDefaultValue(defaultValue);
                    model.setDescription(desc);
                    answer.add(model);
                }
            });
        }

        return answer;
    }

    private boolean updateAutoConfigureOptions(File file, String changed) throws MojoExecutionException {
        try {
            if (!file.exists()) {
                // include markers for new files
                changed = "// spring-boot-auto-configure options: START\n" + changed + "\n// spring-boot-auto-configure options: END\n";
                writeText(file, changed);
                return true;
            }

            String text = loadText(new FileInputStream(file));

            String existing = Strings.between(text, "// spring-boot-auto-configure options: START", "// spring-boot-auto-configure options: END");
            if (existing != null) {
                // remove leading line breaks etc
                existing = existing.trim();
                changed = changed.trim();
                if (existing.equals(changed)) {
                    return false;
                } else {
                    String before = Strings.before(text, "// spring-boot-auto-configure options: START");
                    String after = Strings.after(text, "// spring-boot-auto-configure options: END");
                    text = before + "// spring-boot-auto-configure options: START\n" + changed + "\n// spring-boot-auto-configure options: END" + after;
                    writeText(file, text);
                    return true;
                }
            } else {
                getLog().warn("Cannot find markers in file " + file);
                getLog().warn("Add the following markers");
                getLog().warn("\t// spring-boot-auto-configure options: START");
                getLog().warn("\t// spring-boot-auto-configure options: END");
                if (isFailFast()) {
                    throw new MojoExecutionException("Failed build due failFast=true");
                }
                return false;
            }
        } catch (Exception e) {
            throw new MojoExecutionException("Error reading file " + file + " Reason: " + e, e);
        }
    }

    private String templateAutoConfigurationOptions(List<SpringBootAutoConfigureOptionModel> options, String componentName) throws MojoExecutionException {
        SpringBootModel model = new SpringBootModel();
        model.setGroupId(project.getGroupId());
        model.setArtifactId("camel-" + componentName + "-starter");
        model.setVersion(project.getVersion());
        model.setOptions(options);
        model.setTitle(componentName);

        try {
            String template = loadText(UpdateSpringBootAutoConfigurationReadmeMojo.class.getClassLoader().getResourceAsStream("spring-boot-auto-configure-options.mvel"));
            String out = (String) TemplateRuntime.eval(template, model, Collections.singletonMap("util", MvelHelper.INSTANCE));
            return out;
        } catch (Exception e) {
            throw new MojoExecutionException("Error processing mvel template. Reason: " + e, e);
        }
    }

    private boolean isFailFast() {
        return failFast != null && failFast;
    }

    private boolean isFailOnNoDescription() {
        return failOnMissingDescription != null && failOnMissingDescription;
    }

}
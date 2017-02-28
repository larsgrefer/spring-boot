/*
 * Copyright 2012-2017 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.boot.autoconfigure.condition;

import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

import java.util.Map;

/**
 * {@link org.springframework.context.annotation.Condition Condition} that checks the
 * {@link Package#getSpecificationVersion() Specification-Version} and/or the
 * {@link Package#getImplementationVersion() Implementation-Version} of a given
 * {@link Class class} or {@link Package package}
 *
 * @author Lars Grefer
 */
class OnVersionCondition extends SpringBootCondition {
    @Override
    public ConditionOutcome getMatchOutcome(ConditionContext context, AnnotatedTypeMetadata metadata) {

        ConditionMessage message = ConditionMessage.empty();

        Package pkg = null;
        String versionRegex = "";
        boolean testSpecVersion = true;
        boolean testImplVersion = true;

        if(metadata.isAnnotated(ConditionalOnPackageVersion.class.getName())) {
            Map<String, Object> annotationAttributes = metadata.getAnnotationAttributes(ConditionalOnPackageVersion.class.getName());

            testImplVersion = (boolean) annotationAttributes.get("testImplementationVersion");
            testSpecVersion = (boolean) annotationAttributes.get("testSpecificationVersion");

            versionRegex = (String) annotationAttributes.get("versionRegex");

            String packageName = (String) annotationAttributes.get("name");

            pkg = Package.getPackage(packageName);

            if(pkg == null) {
                message = message.andCondition(ConditionalOnPackageVersion.class)
                        .didNotFind("package")
                        .items(ConditionMessage.Style.QUOTE, packageName);

                return ConditionOutcome.noMatch(message);
            }

            String specificationVersion;
            String implementationVersion;

            if(testSpecVersion) {
                specificationVersion = pkg.getSpecificationVersion();

                if (specificationVersion != null) {
                    if (specificationVersion.matches(versionRegex)) {
                        message = message.andCondition(ConditionalOnPackageVersion.class)
                                .found("Specification-Version")
                                .items(ConditionMessage.Style.QUOTE, specificationVersion);
                        return ConditionOutcome.match(message);
                    } else {
                        message = message.andCondition(ConditionalOnPackageVersion.class)
                                .because("'" + specificationVersion + "' does not match '" + versionRegex + "'");
                    }
                } else {
                    message = message.andCondition(ConditionalOnPackageVersion.class)
                            .notAvailable("Specification-Version");
                }
            }

            if(testImplVersion) {
                implementationVersion = pkg.getImplementationVersion();

                if(implementationVersion != null) {
                    if(implementationVersion.matches(versionRegex)) {
                        message = message.andCondition(ConditionalOnPackageVersion.class)
                                .found("Implementation-Version")
                                .items(ConditionMessage.Style.QUOTE, implementationVersion);
                    } else {
                        message = message.andCondition(ConditionalOnPackageVersion.class)
                                .because("'" + implementationVersion + "' does not match '" + versionRegex + "'");
                    }
                } else {
                    message = message.andCondition(ConditionalOnPackageVersion.class)
                            .notAvailable("Implementation-Version");
                }
            }

        }

        return ConditionOutcome.noMatch(message);
    }
}

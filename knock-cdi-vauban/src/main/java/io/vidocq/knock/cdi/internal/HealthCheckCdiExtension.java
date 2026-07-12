/*
 * Copyright (c) 2026 Yann Blazart, Antoine Sabot-Durand and the Vidocq contributors
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * This Source Code may also be made available under the following Secondary
 * Licenses when the conditions for such availability set forth in the Eclipse
 * Public License, v. 2.0 are satisfied: GNU General Public License, version 2
 * or any later version, which is available at
 * https://www.gnu.org/licenses/old-licenses/gpl-2.0.html
 *
 * It is also made available under the European Union Public Licence v. 1.2,
 * which is available at
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * SPDX-License-Identifier: EPL-2.0 OR EUPL-1.2 OR GPL-2.0-or-later
 */
package io.vidocq.knock.cdi.internal;

import jakarta.enterprise.inject.build.compatible.spi.BuildCompatibleExtension;
import jakarta.enterprise.inject.build.compatible.spi.Messages;
import jakarta.enterprise.inject.build.compatible.spi.Registration;
import jakarta.enterprise.lang.model.declarations.ClassInfo;
import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.Liveness;
import org.eclipse.microprofile.health.Readiness;
import org.eclipse.microprofile.health.Startup;

import jakarta.enterprise.inject.build.compatible.spi.BeanInfo;

/**
 * Knock Build Compatible Extension — warns about CDI beans implementing
 * {@link HealthCheck} without a probe qualifier.
 *
 * <p>MicroProfile Health 4.0 §2 (Different kinds of Health Checks): "A HealthCheck
 * procedure with none of the above annotations is not an active procedure and
 * should be ignored." The TCK ({@code EnforceQualifierTest}) deploys such a bean
 * and expects a successful deployment whose {@code /health} response carries an
 * empty checks array — so this must NOT be a deployment error, only a warning to
 * help developers spot a probably-forgotten qualifier.</p>
 *
 * <p>This BCE only reports — registration in the registry is delegated to
 * {@link HealthCheckRegistrar} via standard CDI injection, which naturally skips
 * unqualified beans (it resolves {@code @Liveness}/{@code @Readiness}/{@code @Startup}
 * instances only).</p>
 *
 * <p>Discovered via ServiceLoader:
 * {@code META-INF/services/jakarta.enterprise.inject.build.compatible.spi.BuildCompatibleExtension}
 * and {@code provides ... with} in {@code module-info.java}.</p>
 */
public class HealthCheckCdiExtension implements BuildCompatibleExtension {

    private static final String LIVENESS  = Liveness.class.getName();
    private static final String READINESS = Readiness.class.getName();
    private static final String STARTUP   = Startup.class.getName();

    /**
     * Spec §2: a {@link HealthCheck} bean without any of the three probe qualifiers
     * is not an active procedure. It is ignored at registration time; report a
     * warning so the omission is visible.
     *
     * @param bean     the CDI bean to inspect
     * @param messages deployment message collector
     */
    @Registration(types = HealthCheck.class)
    public void warnOnMissingProbeQualifier(BeanInfo bean, Messages messages) {
        boolean hasProbe = bean.qualifiers().stream()
                .anyMatch(q -> LIVENESS.equals(q.name())
                            || READINESS.equals(q.name())
                            || STARTUP.equals(q.name()));

        if (!hasProbe) {
            ClassInfo declaring = bean.declaringClass();
            String className = declaring != null ? declaring.name() : "(unknown)";
            messages.warn(
                    "Knock CDI: the HealthCheck bean '" + className
                    + "' has no @Liveness, @Readiness, or @Startup qualifier;"
                    + " it is not an active procedure and will be ignored"
                    + " (MicroProfile Health 4.0 spec §2)",
                    bean);
        }
    }
}
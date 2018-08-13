/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2018 Oracle and/or its affiliates. All rights reserved.
 *
 * The contents of this file are subject to the terms of either the GNU
 * General Public License Version 2 only ("GPL") or the Common Development
 * and Distribution License("CDDL") (collectively, the "License").  You
 * may not use this file except in compliance with the License.  You can
 * obtain a copy of the License at
 * https://glassfish.java.net/public/CDDL+GPL_1_1.html
 * or packager/legal/LICENSE.txt.  See the License for the specific
 * language governing permissions and limitations under the License.
 *
 * When distributing the software, include this License Header Notice in each
 * file and include the License file at packager/legal/LICENSE.txt.
 *
 * GPL Classpath Exception:
 * Oracle designates this particular file as subject to the "Classpath"
 * exception as provided by Oracle in the GPL Version 2 section of the License
 * file that accompanied this code.
 *
 * Modifications:
 * If applicable, add the following below the License Header, with the fields
 * enclosed by brackets [] replaced by your own identifying information:
 * "Portions Copyright [year] [name of copyright owner]"
 *
 * Contributor(s):
 * If you wish your version of this file to be governed by only the CDDL or
 * only the GPL Version 2, indicate your decision by adding "[Contributor]
 * elects to include this software in this distribution under the [CDDL or GPL
 * Version 2] license."  If you don't indicate a single choice of license, a
 * recipient has the option to distribute your version of this file under
 * either the CDDL, the GPL Version 2 or to extend the choice of license to
 * its licensees as provided above.  However, if you add GPL Version 2 code
 * and therefore, elected the GPL Version 2 license, then the option applies
 * only if the new code is made subject to such option by the copyright
 * holder.
 */
package org.glassfish.build.utils;

import java.io.File;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;
import org.apache.maven.plugin.logging.Log;
import org.apache.tools.ant.BuildEvent;
import org.apache.tools.ant.BuildListener;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.types.ZipFileSet;

/**
 *
 * @author rgrecour
 */
class ZipHelper implements BuildListener {

    private final org.apache.tools.ant.taskdefs.Zip zip;
    private final Project antProject;
    private Log log;

    private ZipHelper() {
        antProject = new Project();
        antProject.addBuildListener((BuildListener) this);
        zip = new org.apache.tools.ant.taskdefs.Zip();
    }

    private static class LazyHolder {
        static final ZipHelper INSTANCE = new ZipHelper();
    }

    static ZipHelper getInstance() {
        return LazyHolder.INSTANCE;
    }

    void zip(Properties properties,
                     Log log,
                     String duplicate,
                     List<ZipFileSet> fsets,
                     File target) {

        this.log = log;
        Iterator it = properties.keySet().iterator();
        while (it.hasNext()) {
            String key = (String) it.next();
            antProject.setProperty(key, properties.getProperty(key));
        }
        zip.setProject(antProject);
        zip.setDestFile(target);
        org.apache.tools.ant.taskdefs.Zip.Duplicate df
                = new org.apache.tools.ant.taskdefs.Zip.Duplicate();
        df.setValue(duplicate);
        zip.setDuplicate(df);
        log.info(String.format("[zip] duplicate: %s", duplicate));

        if (fsets == null) {
            fsets = new ArrayList<ZipFileSet>();
        }

        if (fsets.isEmpty()) {
            ZipFileSet zfs = MavenUtils.createZipFileSet(new File(""), "", "");
            // work around for 
            // http://issues.apache.org/bugzilla/show_bug.cgi?id=42122
            zfs.setDirMode("755");
            zfs.setFileMode("644");
            fsets.add(zfs);
        }

        for (ZipFileSet fset : fsets) {
            zip.addZipfileset(fset);
            String desc = fset.getDescription();
            if (desc != null && !desc.isEmpty()) {
                log.info(String.format("[zip] %s", desc));
            }
        }
        zip.executeMain();
    }

    @Override
    public void buildStarted(BuildEvent event) {
    }

    @Override
    public void buildFinished(BuildEvent event) {
    }

    @Override
    public void targetStarted(BuildEvent event) {
    }

    @Override
    public void targetFinished(BuildEvent event) {
    }

    @Override
    public void taskStarted(BuildEvent event) {
    }

    @Override
    public void taskFinished(BuildEvent event) {
    }

    @Override
    public void messageLogged(BuildEvent event) {
        if (event.getPriority() < 3) {
            log.info(String.format("[zip] %s", event.getMessage()));
        } else {
            log.debug(String.format("[zip] %s", event.getMessage()));
        }
    }
}

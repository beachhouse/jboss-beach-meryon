/*
 * JBoss, Home of Professional Open Source.
 * Copyright (c) 2013, Red Hat, Inc., and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.jboss.meryon.main;

import org.w3c.dom.Document;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
import java.io.File;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitOption;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.EnumSet;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * @author <a href="mailto:cdewolf@redhat.com">Carlo de Wolf</a>
 */
public class Main {
    private static interface Reporter<E> {
        StringBuilder compare(E o1, E o2);
    }

    private static <R> StringBuilder diff(final Map<String, R> set1, final Map<String, R> set2, final Reporter<R> reporter) {
        final StringBuilder report = new StringBuilder();
        final SortedSet<String> all = new TreeSet<>();
        all.addAll(set1.keySet());
        all.addAll(set2.keySet());

        for (String name : all) {
            final R m1 = set1.get(name);
            final R m2 = set2.get(name);
            final StringBuilder sb = reporter.compare(m1, m2);
            if (sb != null)
                report.append(sb);
        }
        return report;
    }

    private static StringBuilder diff(final Map<String, JavaArchive> set1, final Map<String, JavaArchive> set2) {
        final Map<String, Pkg> allPackages1 = new HashMap<>();
        for (JavaArchive archive : set1.values())
            allPackages1.putAll(archive.getPackages());
        final Map<String, Pkg> allPackages2 = new HashMap<>();
        for (JavaArchive archive : set2.values())
            allPackages2.putAll(archive.getPackages());
        return diff(allPackages1, allPackages2, new Reporter<Pkg>() {
            @Override
            public StringBuilder compare(Pkg o1, Pkg o2) {
                if (o1 != null) {
                    if (o2 != null) {
                        final StringBuilder sb = diff(o1.getClasses(), o2.getClasses(), new Reporter<Cls>() {
                            @Override
                            public StringBuilder compare(Cls o1, Cls o2) {
                                if (o1 != null) {
                                    if (o2 != null) {
                                        return null;
                                    }
                                    else {
                                        if (isInteresting(o1))
                                            return new StringBuilder("      - " + o1.getName() + "\n");
                                        return null;
                                    }
                                }
                                else {
                                    assert o2 != null;
                                    if (isInteresting(o2))
                                        return new StringBuilder("      + " + o2.getName() + "\n");
                                    return null;
                                }
                            }
                        });
                        if (sb.length() > 0)
                            sb.insert(0, "    ~ " + o1.getName() + "\n");
                        return sb;
                    }
                    else {
                        return new StringBuilder("    - " + o1.getName() + "\n");
                    }
                }
                else {
                    assert o2 != null;
                    return new StringBuilder("    + " + o2.getName() + "\n");
                }
            }
        });
    }

    public static void main(final String[] args) {
        try {
            final SortedMap<String, Module> install1 = scanModules(args[0]);
            final SortedMap<String, Module> install2 = scanModules(args[1]);

            final StringBuilder report = diff(install1, install2, new Reporter<Module>() {
                @Override
                public StringBuilder compare(Module o1, Module o2) {
                    if (o1 != null) {
                        if (o2 != null) {
                            final StringBuilder sb = diff(o1.getResources(), o2.getResources());
                            if (sb.length() > 0)
                                sb.insert(0, "  ~ module " + o1.getName() + " changed\n");
                            return sb;
                        }
                        else {
                            return new StringBuilder("  - module " + o1.getName() + " deleted\n");
                        }
                    }
                    else {
                        assert o2 != null;
                        return new StringBuilder("  + module " + o2.getName() + " added\n");
                    }
                }
            });
            System.out.println(report);
        } catch (IOException e) {
            e.printStackTrace();
            System.exit(1);
        }
    }

    private static Module getModule(final SortedMap<String, Module> modules, final String moduleName) {
        Module module = modules.get(moduleName);
        if (module != null)
            return module;
        module = new Module(moduleName);
        modules.put(moduleName, module);
        return module;
    }

    private static boolean isDigit(final CharSequence cs) {
        final int l = cs.length();
        for (int i = 0 ; i < l; i++) {
            if (!Character.isDigit(cs.charAt(i)))
                return false;
        }
        return true;
    }

    private static boolean isInteresting(final Path file) {
        // we are actually only interested in jar files, but I rather opt-out at the moment to ensure we have coverage
        final String fileName = file.toString();
        if (fileName.endsWith(".css"))
            return false;
        if (fileName.endsWith(".html"))
            return false;
        if (fileName.endsWith(".ico"))
            return false;
        if (fileName.endsWith(".index"))
            return false;
        if (fileName.endsWith(".png"))
            return false;
        if (fileName.endsWith(".properties"))
            return false;
        if (fileName.endsWith(".txt"))
            return false;
        if (fileName.contains("META-INF"))
            return false;
        return true;
    }

    private static boolean isInteresting(final Cls cls) {
        // ignore anonymous inner classes
        final String className = cls.getName();
        final int i = className.lastIndexOf('$');
        if (i >= 0) {
            final String sequence = className.substring(i + 1);
            return !isDigit(sequence);
        }
        return true;
    }

    private static boolean isPrivateModule(final Path dir) {
        try {
            final File moduleMetaData = dir.resolve("module.xml").toFile();
            if (!moduleMetaData.exists())
                return false;
            final String jbossAPI = xpath(moduleMetaData, "/module/properties/property[@name='jboss.api']/@value");
            return jbossAPI.equals("private");
        } catch (SAXException e) {
            throw new RuntimeException(e);
        } catch (XPathExpressionException e) {
            throw new RuntimeException(e);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static <T> Iterable<T> iterate(final Enumeration<T> e) {
        return new Iterable<T>() {
            @Override
            public Iterator<T> iterator() {
                return new Iterator<T>() {
                    @Override
                    public boolean hasNext() {
                        return e.hasMoreElements();
                    }

                    @Override
                    public T next() {
                        return e.nextElement();
                    }

                    @Override
                    public void remove() {
                        throw new UnsupportedOperationException("remove");
                    }
                };
            }
        };
    }

    private static SortedMap<String, Module> scanModules(final String path) throws IOException {
        final SortedMap<String, Module> modules = new TreeMap<>();
        final Path start = FileSystems.getDefault().getPath(path, "modules/system/layers/base");
        Files.walkFileTree(start, EnumSet.of(FileVisitOption.FOLLOW_LINKS), Integer.MAX_VALUE,
                new SimpleFileVisitor<Path>() {
                    @Override
                    public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                        if (isPrivateModule(dir))
                            return FileVisitResult.SKIP_SUBTREE;
                        return super.preVisitDirectory(dir, attrs);
                    }

                    @Override
                    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                        if (file.endsWith("module.xml"))
                            return FileVisitResult.CONTINUE;
                        if (!isInteresting(file))
                            return FileVisitResult.CONTINUE;
                        // we found a resource in the module
                        final Path resource = start.relativize(file);
//                        System.out.println(resource);
                        // let the guessing and hacking begin
                        final Path moduleDir = resource.subpath(0, resource.getNameCount() - 2);
                        // TODO: should really be read from module.xml
                        final String moduleName = moduleDir.toString().replace('/', '.');
                        final Module module = getModule(modules, moduleName);
                        final String jarName = resource.getName(resource.getNameCount() - 1).toString();
//                        System.out.println(jarName);
                        final JarFile jarFile = new JarFile(file.toFile());
                        final JavaArchive archive = new JavaArchive(jarName);
                        // TODO: read manifest attributes to determine version stuff?
                        for (JarEntry entry : iterate(jarFile.entries())) {
                            final String name = entry.getName();
                            if (!name.endsWith(".class"))
                                continue;
                            final String className = name.substring(0, name.length() - 6).replace('/', '.');
                            archive.addClass(className);
                        }
                        module.addResource(archive);
                        return FileVisitResult.CONTINUE;
                    }

//                        @Override
//                        public FileVisitResult visitFileFailed(Path file, IOException exc) throws IOException {
//                            throw new RuntimeException("NYI: .visitFileFailed");
//                        }
//
//                        @Override
//                        public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
//                            throw new RuntimeException("NYI: .postVisitDirectory");
//                        }
                });
        return modules;
    }

    private static String xpath(final File file, final String expression) throws IOException, SAXException, XPathExpressionException {
        try {
            final DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            final DocumentBuilder builder = factory.newDocumentBuilder();
            final Document doc = builder.parse(file);
            final XPathFactory xPathfactory = XPathFactory.newInstance();
            final XPath xpath = xPathfactory.newXPath();
            final XPathExpression expr = xpath.compile(expression);
            return expr.evaluate(doc);
        } catch (ParserConfigurationException e) {
            throw new RuntimeException(e);
        }
    }
}

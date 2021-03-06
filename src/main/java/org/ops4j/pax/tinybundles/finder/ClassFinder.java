/*
 * Copyright 2013 Harald Wellmann
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
 * implied.
 *
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.ops4j.pax.tinybundles.finder;

import java.io.File;
import java.io.IOException;
import java.net.JarURLConnection;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import org.osgi.framework.Bundle;
import org.osgi.framework.FrameworkUtil;

/**
 * @author Harald Wellmann
 * 
 */
public class ClassFinder
{
    public Collection<ClassDescriptor> findAllEmbeddedClasses( Class<?> klass ) throws IOException
    {
        String resourcePrefix = klass.getName().replace( '.', '/' ) + "\\$.*";
        return findAllEmbeddedClasses( klass, resourcePrefix );
    }

    public Collection<ClassDescriptor> findAnonymousClasses( Class<?> klass ) throws IOException
    {
        String resourcePrefix = klass.getName().replace( '.', '/' ) + "\\$\\d.*";
        return findAllEmbeddedClasses( klass, resourcePrefix );
    }

    public Collection<ClassDescriptor> findAllEmbeddedClasses( Class<?> klass, String pattern )
        throws IOException
    {
        ClassLoader classLoader = klass.getClassLoader();
        if( classLoader == null )
        {
            classLoader = ClassLoader.getSystemClassLoader();
        }
        URL classUrl = classLoader.getResource( asResource( klass ) );
        if( classUrl.getProtocol().equals( "jar" ) )
        {

            String jarPath = classUrl.getFile();
            int bang = jarPath.indexOf( "!" );
            if( bang > -1 )
            {
                jarPath = jarPath.substring( 0, bang );
            }
            URL url = new URL( "jar:" + jarPath + "!/" );
            return findEmbeddedClasses( url, pattern );
        }
        else if( classUrl.getProtocol().equals( "file" ) )
        {
            File classFile;
            try
            {
                classFile = new File( classUrl.toURI() );
            }
            catch ( URISyntaxException exc )
            {
                throw new IllegalStateException( exc );
            }
            return findEmbeddedClasses( classFile, pattern );
        }
        else 
        {
            Bundle bundle = FrameworkUtil.getBundle(klass);
            if (bundle != null) {
                String path = klass.getPackage().getName().replace('.', '/');
                String filePattern = klass.getSimpleName() + "$*";
                Enumeration<URL> urls = bundle.findEntries(path, filePattern, false);
                if (urls != null)
                {
                    return findEmbeddedClasses(urls, pattern);
                } else {
                    return new ArrayList<ClassDescriptor>();
                }
            }
            else
            {
                throw new IllegalArgumentException( "No bundle found for class " + klass + ". Unsupported woven or system package classes");
            }
        }
//        throw new IllegalStateException( "unsupported protocol " + classUrl.getProtocol() );
    }
    
    public List<ClassDescriptor> findEmbeddedClasses( Enumeration<URL> urls, String pattern )
            throws MalformedURLException
    {
        String filePattern = "/" + pattern;
        List<ClassDescriptor> descriptors = new ArrayList<ClassDescriptor>();
        while (urls.hasMoreElements()) {
            URL url = urls.nextElement();
            if (url.getFile().matches(filePattern)){
                ClassDescriptor descriptor =
                        new ClassDescriptor( url.getFile(), url );
                    descriptors.add( descriptor );
            }
        }
        return descriptors;
    }

    public List<ClassDescriptor> findEmbeddedClasses( File file, String pattern )
        throws MalformedURLException
    {
        File dir = file.getParentFile();
        int slash = pattern.lastIndexOf( '/' );
        String path = pattern.substring( 0, slash + 1 );
        String filePattern = pattern.substring( slash + 1 );

        List<ClassDescriptor> descriptors = new ArrayList<ClassDescriptor>();
        for( File f : dir.listFiles() )
        {
            if( f.getName().matches( filePattern ) )
            {
                ClassDescriptor descriptor =
                    new ClassDescriptor( path + f.getName(), f.toURI().toURL() );
                descriptors.add( descriptor );
            }
        }
        return descriptors;
    }

    public List<ClassDescriptor> findEmbeddedClasses( URL jarUrl, String pattern )
        throws IOException
    {
        JarURLConnection connection = (JarURLConnection) jarUrl.openConnection();
        JarFile jarFile = connection.getJarFile();
        List<ClassDescriptor> descriptors = new ArrayList<ClassDescriptor>();
        Enumeration<JarEntry> entries = jarFile.entries();
        while( entries.hasMoreElements() )
        {
            JarEntry entry = entries.nextElement();
            if( entry.getName().matches( pattern ) )
            {
                String entryUrl = jarUrl.toExternalForm() + entry.getName();
                ClassDescriptor descriptor =
                    new ClassDescriptor( entry.getName(), new URL( entryUrl ) );
                descriptors.add( descriptor );
            }
        }
        jarFile.close();
        return descriptors;
    }

    public static String asResource( Class<?> klass )
    {
        String name = klass.getName().replace( '.', '/' ) + ".class";
        return name;
    }
}

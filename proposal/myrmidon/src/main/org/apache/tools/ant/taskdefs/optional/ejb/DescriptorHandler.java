/*
 * Copyright (C) The Apache Software Foundation. All rights reserved.
 *
 * This software is published under the terms of the Apache Software License
 * version 1.1, a copy of which has been included with this distribution in
 * the LICENSE.txt file.
 */
package org.apache.tools.ant.taskdefs.optional.ejb;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Hashtable;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.Task;
import org.xml.sax.AttributeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

/**
 * Inner class used by EjbJar to facilitate the parsing of deployment
 * descriptors and the capture of appropriate information. Extends HandlerBase
 * so it only implements the methods needed. During parsing creates a hashtable
 * consisting of entries mapping the name it should be inserted into an EJB jar
 * as to a File representing the file on disk. This list can then be accessed
 * through the getFiles() method.
 *
 * @author RT
 */
public class DescriptorHandler extends org.xml.sax.HandlerBase
{
    private final static int STATE_LOOKING_EJBJAR = 1;
    private final static int STATE_IN_EJBJAR = 2;
    private final static int STATE_IN_BEANS = 3;
    private final static int STATE_IN_SESSION = 4;
    private final static int STATE_IN_ENTITY = 5;
    private final static int STATE_IN_MESSAGE = 6;

    /**
     * Bunch of constants used for storing entries in a hashtable, and for
     * constructing the filenames of various parts of the ejb jar.
     */
    private final static String EJB_REF = "ejb-ref";
    private final static String HOME_INTERFACE = "home";
    private final static String REMOTE_INTERFACE = "remote";
    private final static String LOCAL_HOME_INTERFACE = "local-home";
    private final static String LOCAL_INTERFACE = "local";
    private final static String BEAN_CLASS = "ejb-class";
    private final static String PK_CLASS = "prim-key-class";
    private final static String EJB_NAME = "ejb-name";
    private final static String EJB_JAR = "ejb-jar";
    private final static String ENTERPRISE_BEANS = "enterprise-beans";
    private final static String ENTITY_BEAN = "entity";
    private final static String SESSION_BEAN = "session";
    private final static String MESSAGE_BEAN = "message-driven";

    private String publicId = null;

    /**
     * The state of the parsing
     */
    private int parseState = STATE_LOOKING_EJBJAR;

    /**
     * Instance variable used to store the name of the current element being
     * processed by the SAX parser. Accessed by the SAX parser call-back methods
     * startElement() and endElement().
     */
    protected String currentElement = null;

    /**
     * The text of the current element
     */
    protected String currentText = null;

    /**
     * Instance variable that stores the names of the files as they will be put
     * into the jar file, mapped to File objects Accessed by the SAX parser
     * call-back method characters().
     */
    protected Hashtable ejbFiles = null;

    /**
     * Instance variable that stores the value found in the &lt;ejb-name&gt;
     * element
     */
    protected String ejbName = null;

    private Hashtable fileDTDs = new Hashtable();

    private Hashtable resourceDTDs = new Hashtable();

    private boolean inEJBRef = false;

    private Hashtable urlDTDs = new Hashtable();

    private Task owningTask;

    /**
     * The directory containing the bean classes and interfaces. This is used
     * for performing dependency file lookups.
     */
    private File srcDir;

    public DescriptorHandler( Task task, File srcDir )
    {
        this.owningTask = task;
        this.srcDir = srcDir;
    }

    /**
     * Getter method that returns the value of the &lt;ejb-name&gt; element.
     *
     * @return The EjbName value
     */
    public String getEjbName()
    {
        return ejbName;
    }

    /**
     * Getter method that returns the set of files to include in the EJB jar.
     *
     * @return The Files value
     */
    public Hashtable getFiles()
    {
        return ( ejbFiles == null ) ? new Hashtable() : ejbFiles;
    }

    /**
     * Get the publicId of the DTD
     *
     * @return The PublicId value
     */
    public String getPublicId()
    {
        return publicId;
    }

    /**
     * SAX parser call-back method invoked whenever characters are located
     * within an element. currentAttribute (modified by startElement and
     * endElement) tells us whether we are in an interesting element (one of the
     * up to four classes of an EJB). If so then converts the classname from the
     * format org.apache.tools.ant.Parser to the convention for storing such a
     * class, org/apache/tools/ant/Parser.class. This is then resolved into a
     * file object under the srcdir which is stored in a Hashtable.
     *
     * @param ch A character array containing all the characters in the element,
     *      and maybe others that should be ignored.
     * @param start An integer marking the position in the char array to start
     *      reading from.
     * @param length An integer representing an offset into the char array where
     *      the current data terminates.
     * @exception SAXException Description of Exception
     */
    public void characters( char[] ch, int start, int length )
        throws SAXException
    {

        currentText += new String( ch, start, length );
    }

    /**
     * SAX parser call-back method that is invoked when an element is exited.
     * Used to blank out (set to the empty string, not nullify) the name of the
     * currentAttribute. A better method would be to use a stack as an instance
     * variable, however since we are only interested in leaf-node data this is
     * a simpler and workable solution.
     *
     * @param name The name of the attribute being exited. Ignored in this
     *      implementation.
     * @exception SAXException Description of Exception
     */
    public void endElement( String name )
        throws SAXException
    {
        processElement();
        currentText = "";
        this.currentElement = "";
        if( name.equals( EJB_REF ) )
        {
            inEJBRef = false;
        }
        else if( parseState == STATE_IN_ENTITY && name.equals( ENTITY_BEAN ) )
        {
            parseState = STATE_IN_BEANS;
        }
        else if( parseState == STATE_IN_SESSION && name.equals( SESSION_BEAN ) )
        {
            parseState = STATE_IN_BEANS;
        }
        else if( parseState == STATE_IN_MESSAGE && name.equals( MESSAGE_BEAN ) )
        {
            parseState = STATE_IN_BEANS;
        }
        else if( parseState == STATE_IN_BEANS && name.equals( ENTERPRISE_BEANS ) )
        {
            parseState = STATE_IN_EJBJAR;
        }
        else if( parseState == STATE_IN_EJBJAR && name.equals( EJB_JAR ) )
        {
            parseState = STATE_LOOKING_EJBJAR;
        }
    }

    public void registerDTD( String publicId, String location )
    {
        if( location == null )
        {
            return;
        }

        File fileDTD = new File( location );
        if( fileDTD.exists() )
        {
            if( publicId != null )
            {
                fileDTDs.put( publicId, fileDTD );
                owningTask.log( "Mapped publicId " + publicId + " to file " + fileDTD, Project.MSG_VERBOSE );
            }
            return;
        }

        if( getClass().getResource( location ) != null )
        {
            if( publicId != null )
            {
                resourceDTDs.put( publicId, location );
                owningTask.log( "Mapped publicId " + publicId + " to resource " + location, Project.MSG_VERBOSE );
            }
        }

        try
        {
            if( publicId != null )
            {
                URL urldtd = new URL( location );
                urlDTDs.put( publicId, urldtd );
            }
        }
        catch( java.net.MalformedURLException e )
        {
            //ignored
        }

    }

    public InputSource resolveEntity( String publicId, String systemId )
        throws SAXException
    {
        this.publicId = publicId;

        File dtdFile = (File)fileDTDs.get( publicId );
        if( dtdFile != null )
        {
            try
            {
                owningTask.log( "Resolved " + publicId + " to local file " + dtdFile, Project.MSG_VERBOSE );
                return new InputSource( new FileInputStream( dtdFile ) );
            }
            catch( FileNotFoundException ex )
            {
                // ignore
            }
        }

        String dtdResourceName = (String)resourceDTDs.get( publicId );
        if( dtdResourceName != null )
        {
            InputStream is = this.getClass().getResourceAsStream( dtdResourceName );
            if( is != null )
            {
                owningTask.log( "Resolved " + publicId + " to local resource " + dtdResourceName, Project.MSG_VERBOSE );
                return new InputSource( is );
            }
        }

        URL dtdUrl = (URL)urlDTDs.get( publicId );
        if( dtdUrl != null )
        {
            try
            {
                InputStream is = dtdUrl.openStream();
                owningTask.log( "Resolved " + publicId + " to url " + dtdUrl, Project.MSG_VERBOSE );
                return new InputSource( is );
            }
            catch( IOException ioe )
            {
                //ignore
            }
        }

        owningTask.log( "Could not resolve ( publicId: " + publicId + ", systemId: " + systemId + ") to a local entity",
                        Project.MSG_INFO );

        return null;
    }

    /**
     * SAX parser call-back method that is used to initialize the values of some
     * instance variables to ensure safe operation.
     *
     * @exception SAXException Description of Exception
     */
    public void startDocument()
        throws SAXException
    {
        this.ejbFiles = new Hashtable( 10, 1 );
        this.currentElement = null;
        inEJBRef = false;
    }

    /**
     * SAX parser call-back method that is invoked when a new element is entered
     * into. Used to store the context (attribute name) in the currentAttribute
     * instance variable.
     *
     * @param name The name of the element being entered.
     * @param attrs Attributes associated to the element.
     * @exception SAXException Description of Exception
     */
    public void startElement( String name, AttributeList attrs )
        throws SAXException
    {
        this.currentElement = name;
        currentText = "";
        if( name.equals( EJB_REF ) )
        {
            inEJBRef = true;
        }
        else if( parseState == STATE_LOOKING_EJBJAR && name.equals( EJB_JAR ) )
        {
            parseState = STATE_IN_EJBJAR;
        }
        else if( parseState == STATE_IN_EJBJAR && name.equals( ENTERPRISE_BEANS ) )
        {
            parseState = STATE_IN_BEANS;
        }
        else if( parseState == STATE_IN_BEANS && name.equals( SESSION_BEAN ) )
        {
            parseState = STATE_IN_SESSION;
        }
        else if( parseState == STATE_IN_BEANS && name.equals( ENTITY_BEAN ) )
        {
            parseState = STATE_IN_ENTITY;
        }
        else if( parseState == STATE_IN_BEANS && name.equals( MESSAGE_BEAN ) )
        {
            parseState = STATE_IN_MESSAGE;
        }
    }

    protected void processElement()
    {
        if( inEJBRef ||
            ( parseState != STATE_IN_ENTITY && parseState != STATE_IN_SESSION && parseState != STATE_IN_MESSAGE ) )
        {
            return;
        }

        if( currentElement.equals( HOME_INTERFACE ) ||
            currentElement.equals( REMOTE_INTERFACE ) ||
            currentElement.equals( LOCAL_INTERFACE ) ||
            currentElement.equals( LOCAL_HOME_INTERFACE ) ||
            currentElement.equals( BEAN_CLASS ) ||
            currentElement.equals( PK_CLASS ) )
        {

            // Get the filename into a String object
            File classFile = null;
            String className = currentText.trim();

            // If it's a primitive wrapper then we shouldn't try and put
            // it into the jar, so ignore it.
            if( !className.startsWith( "java." ) &&
                !className.startsWith( "javax." ) )
            {
                // Translate periods into path separators, add .class to the
                // name, create the File object and add it to the Hashtable.
                className = className.replace( '.', File.separatorChar );
                className += ".class";
                classFile = new File( srcDir, className );
                ejbFiles.put( className, classFile );
            }
        }

        // Get the value of the <ejb-name> tag.  Only the first occurence.
        if( currentElement.equals( EJB_NAME ) )
        {
            if( ejbName == null )
            {
                ejbName = currentText.trim();
            }
        }
    }
}

/*
 * Copyright (C) The Apache Software Foundation. All rights reserved.
 *
 * This software is published under the terms of the Apache Software License
 * version 1.1, a copy of which has been included with this distribution in
 * the LICENSE.txt file.
 */
package org.apache.tools.ant.taskdefs.rmic;

import org.apache.myrmidon.api.TaskException;
import org.apache.tools.ant.taskdefs.Rmic;
import org.apache.tools.ant.types.Path;
import org.apache.tools.ant.util.FileNameMapper;

/**
 * The interface that all rmic adapters must adher to. <p>
 *
 * A rmic adapter is an adapter that interprets the rmic's parameters in
 * preperation to be passed off to the compiler this adapter represents. As all
 * the necessary values are stored in the Rmic task itself, the only thing all
 * adapters need is the rmic task, the execute command and a parameterless
 * constructor (for reflection).</p>
 *
 * @author <a href="mailto:tokamoto@rd.nttdata.co.jp">Takashi Okamoto</a>
 * @author <a href="mailto:stefan.bodewig@epost.de">Stefan Bodewig</a>
 */

public interface RmicAdapter
{

    /**
     * Sets the rmic attributes, which are stored in the Rmic task.
     *
     * @param attributes The new Rmic value
     */
    void setRmic( Rmic attributes );

    /**
     * Executes the task.
     *
     * @return has the compilation been successful
     * @exception TaskException Description of Exception
     */
    boolean execute()
        throws TaskException;

    /**
     * Maps source class files to the files generated by this rmic
     * implementation.
     *
     * @return The Mapper value
     */
    FileNameMapper getMapper();

    /**
     * The CLASSPATH this rmic process will use.
     *
     * @return The Classpath value
     */
    Path getClasspath()
        throws TaskException;
}

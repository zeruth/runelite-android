/*
 * Copyright (c) 2026, Tyler Bochard
 * All rights reserved.
 *
 * Part of android-awt, the Android shadow of the desktop JDK surface used by
 * runelite-android. Original work of this project, not derived from OpenJDK;
 * licensed under the BSD 2-Clause License (see the LICENSE file at the
 * repository root). Files ported from OpenJDK carry the OpenJDK header instead;
 * see android-awt/LICENSE.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
 * FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package javax.swing.event;

import java.util.EventObject;
import javax.swing.tree.TreePath;

public class TreeModelEvent extends EventObject {
    private static final long serialVersionUID = 5061611472566089751L;
    protected TreePath path;
    protected int[] childIndices;
    protected Object[] children;

    public TreeModelEvent(Object source, TreePath path) {
        super(source);
        this.path = path;
    }

    public TreeModelEvent(Object source, TreePath path, int[] indices, Object[] children) {
        super(source);
        this.path = path; this.childIndices = indices; this.children = children;
    }

    public TreeModelEvent(Object source, Object[] path) {
        super(source);
        this.path = path == null ? null : new TreePath(path);
    }

    public TreeModelEvent(Object source, Object[] path, int[] indices, Object[] children) {
        this(source, path == null ? null : new TreePath(path), indices, children);
    }
    public Object[] getPath() { return path == null ? null : path.getPath(); }
    public TreePath getTreePath() { return path; }
    public int[] getChildIndices() { return childIndices == null ? null : childIndices.clone(); }
    public Object[] getChildren() { return children == null ? null : children.clone(); }
}

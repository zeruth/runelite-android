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
package java.awt.geom;
import java.util.*;
/** Adaptive subdivision of quadratic and cubic segments into line segments. */
public class FlatteningPathIterator implements PathIterator {
    private final List<double[]> segments=new ArrayList<>();
    private final int winding,limit;
    private final double flatness;
    private int index;
    public FlatteningPathIterator(PathIterator source,double flatness){this(source,flatness,10);}
    public FlatteningPathIterator(PathIterator source,double flatness,int limit){
        if(flatness<0 || limit<0)throw new IllegalArgumentException();
        this.flatness=flatness;this.limit=limit;winding=source.getWindingRule();
        double[] c=new double[6];double x=0,y=0,mx=0,my=0;
        while(!source.isDone()){
            int type=source.currentSegment(c);
            if(type==SEG_MOVETO){x=mx=c[0];y=my=c[1];segments.add(new double[]{type,x,y});}
            else if(type==SEG_LINETO){x=c[0];y=c[1];segments.add(new double[]{type,x,y});}
            else if(type==SEG_QUADTO){curve(new double[]{x,y,c[0],c[1],c[2],c[3]},0);x=c[2];y=c[3];}
            else if(type==SEG_CUBICTO){curve(new double[]{x,y,c[0],c[1],c[2],c[3],c[4],c[5]},0);x=c[4];y=c[5];}
            else{segments.add(new double[]{SEG_CLOSE,0,0});x=mx;y=my;}
            source.next();
        }
    }
    private void curve(double[] p,int depth){
        int n=p.length/2;double dx=p[p.length-2]-p[0],dy=p[p.length-1]-p[1],len=dx*dx+dy*dy,max=0;
        for(int i=1;i<n-1;i++){
            double t=len==0?0:Math.max(0,Math.min(1,((p[2*i]-p[0])*dx+(p[2*i+1]-p[1])*dy)/len));
            double ex=p[2*i]-p[0]-t*dx,ey=p[2*i+1]-p[1]-t*dy;max=Math.max(max,ex*ex+ey*ey);
        }
        if(depth>=limit || max<=flatness*flatness){segments.add(new double[]{SEG_LINETO,p[p.length-2],p[p.length-1]});return;}
        double[] temp=p.clone(),left=new double[p.length],right=new double[p.length];
        for(int k=0;k<n;k++){
            left[2*k]=temp[0];left[2*k+1]=temp[1];right[2*(n-k-1)]=temp[2*(n-k-1)];right[2*(n-k-1)+1]=temp[2*(n-k-1)+1];
            for(int i=0;i<n-k-1;i++){temp[2*i]=(temp[2*i]+temp[2*i+2])/2;temp[2*i+1]=(temp[2*i+1]+temp[2*i+3])/2;}
        }
        curve(left,depth+1);curve(right,depth+1);
    }
    public double getFlatness(){return flatness;}public int getRecursionLimit(){return limit;}
    public int getWindingRule(){return winding;}public boolean isDone(){return index>=segments.size();}public void next(){index++;}
    public int currentSegment(double[] c){if(isDone())throw new NoSuchElementException();double[] s=segments.get(index);c[0]=s[1];c[1]=s[2];return(int)s[0];}
    public int currentSegment(float[] c){double[] d=new double[2];int t=currentSegment(d);c[0]=(float)d[0];c[1]=(float)d[1];return t;}
}

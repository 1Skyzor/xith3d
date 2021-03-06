/**
 * Copyright (c) 2003-2009, Xith3D Project Group all rights reserved.
 * 
 * Portions based on the Java3D interface, Copyright by Sun Microsystems.
 * Many thanks to the developers of Java3D and Sun Microsystems for their
 * innovation and design.
 * 
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 * 
 * Redistributions of source code must retain the above copyright notice,
 * this list of conditions and the following disclaimer.
 * 
 * Redistributions in binary form must reproduce the above copyright notice,
 * this list of conditions and the following disclaimer in the documentation
 * and/or other materials provided with the distribution.
 * 
 * Neither the name of the 'Xith3D Project Group' nor the names of its
 * contributors may be used to endorse or promote products derived from this
 * software without specific prior written permission.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) A
 * RISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE
 */
package org.xith3d.render.jsr231;

import java.nio.FloatBuffer;

import javax.media.opengl.GL;

import org.jagatoo.logging.ProfileTimer;
import org.openmali.vecmath2.Colorf;
import org.openmali.vecmath2.Point3f;
import org.openmali.vecmath2.Tuple3f;
import org.openmali.vecmath2.Vector3f;

import org.xith3d.render.CanvasPeer;
import org.xith3d.render.OpenGLCapabilities;
import org.xith3d.render.OpenGLStatesCache;
import org.xith3d.render.RenderOptions;
import org.xith3d.render.RenderPeer;
import org.xith3d.render.RenderPeer.RenderMode;
import org.xith3d.render.preprocessing.RenderAtom;
import org.xith3d.render.states.StateUnit;
import org.xith3d.render.states.units.LightingStateUnit;
import org.xith3d.render.states.units.StateUnitPeer;
import org.xith3d.scenegraph.AmbientLight;
import org.xith3d.scenegraph.DirectionalLight;
import org.xith3d.scenegraph.Light;
import org.xith3d.scenegraph.PointLight;
import org.xith3d.scenegraph.SpotLight;
import org.xith3d.scenegraph.Transform3D;
import org.xith3d.scenegraph.View;
import org.xith3d.scenegraph._SG_PrivilegedAccess;
import org.xith3d.utility.logging.X3DLog;

import com.sun.opengl.util.BufferUtil;

/**
 * Handles lighting.
 * 
 * @author David Yazel
 * @author Lilian Chamontin [jsr231 port]
 * @author Marvin Froehlich (aka Qudus)
 */
public class LightingStateUnitPeer implements StateUnitPeer
{
    // buffers used to send lighting data to the GL
    private final FloatBuffer floatBuffer = BufferUtil.newFloatBuffer( 4 );
    private static final FloatBuffer blackColorBuffer = BufferUtil.newFloatBuffer( 4 );
    static
    {
        blackColorBuffer.put( 0f );
        blackColorBuffer.put( 0f );
        blackColorBuffer.put( 0f );
        blackColorBuffer.put( 1f );
        blackColorBuffer.rewind();
    }
    
    private final Point3f tmpPoint = new Point3f();
    private final Vector3f tmpVector = new Vector3f();
    
    public LightingStateUnitPeer()
    {
    }
    
    public static final int getGlLight( int l )
    {
        switch ( l )
        {
            case 0:
                return GL.GL_LIGHT0;
            case 1:
                return GL.GL_LIGHT1;
            case 2:
                return GL.GL_LIGHT2;
            case 3:
                return GL.GL_LIGHT3;
            case 4:
                return GL.GL_LIGHT4;
            case 5:
                return GL.GL_LIGHT5;
            case 6:
                return GL.GL_LIGHT6;
            case 7:
                return GL.GL_LIGHT7;
            default:
                Error e = new Error( "Invalid Light" );
                X3DLog.print( e );
                throw e;
        }
    }
    
    // TODO (yvg) add support for separate diffuse and specular lighting
    private final boolean shadeLight( final GL gl, OpenGLStatesCache statesCache, final Light light, final Transform3D transform, final int lightIndex )
    {
        if ( ( light == null ) || !light.isEnabled() || ( lightIndex > 7 ) )
            return ( false );
        
        if ( light instanceof AmbientLight )
        {
            // TODO (yvg) Add support for multiple ambient lights (should
            // accumulate ambient light colors and after clamp to 1,1,1)
            final AmbientLight aLight = (AmbientLight)light;
            final Colorf color = aLight.getColor();
            floatBuffer.clear();
            floatBuffer.put( color.getRed() );
            floatBuffer.put( color.getGreen() );
            floatBuffer.put( color.getBlue() );
            floatBuffer.put( 1 );
            floatBuffer.rewind();
            gl.glLightModelfv( GL.GL_LIGHT_MODEL_AMBIENT, floatBuffer );
            
            return ( false ); // don't incement light-index
        }
        else if ( light instanceof DirectionalLight )
        {
            final int glLight = getGlLight( lightIndex );
            
            final DirectionalLight dLight = (DirectionalLight)light;
            
            final Colorf color = dLight.getColor();
            floatBuffer.clear();
            floatBuffer.put( color.getRed() );
            floatBuffer.put( color.getGreen() );
            floatBuffer.put( color.getBlue() );
            floatBuffer.put( 1.0f );
            floatBuffer.rewind();
            
            gl.glLightfv( glLight, GL.GL_DIFFUSE, floatBuffer );
            gl.glLightfv( glLight, GL.GL_SPECULAR, floatBuffer );
            
            // Set Ambient component of this light to 0
            gl.glLightfv( glLight, GL.GL_AMBIENT, blackColorBuffer );
            
            if ( transform == null )
                tmpVector.set( dLight.getDirection() );
            else
                transform.getMatrix4f().transform( dLight.getDirection(), tmpVector );
            
            floatBuffer.clear();
            floatBuffer.put( -tmpVector.getX() );
            floatBuffer.put( -tmpVector.getY() );
            floatBuffer.put( -tmpVector.getZ() );
            floatBuffer.put( 0.0f );
            floatBuffer.rewind();
            
            gl.glLightfv( glLight, GL.GL_POSITION, floatBuffer );
            
            gl.glLightf( glLight, GL.GL_SPOT_EXPONENT, 0f );
            gl.glLightf( glLight, GL.GL_SPOT_CUTOFF, 180f );
            
            if ( !statesCache.enabled || !statesCache.lightEnabled[ lightIndex ] )
            {
                gl.glEnable( glLight );
                statesCache.lightEnabled[ lightIndex ] = true;
            }
        }
        else if ( light instanceof PointLight )
        {
            final int glLight = getGlLight( lightIndex );
            
            final PointLight pLight = (PointLight)light;
            
            final Colorf color = pLight.getColor();
            floatBuffer.clear();
            floatBuffer.put( color.getRed() );
            floatBuffer.put( color.getGreen() );
            floatBuffer.put( color.getBlue() );
            floatBuffer.put( 1 );
            floatBuffer.rewind();
            
            gl.glLightfv( glLight, GL.GL_DIFFUSE, floatBuffer );
            gl.glLightfv( glLight, GL.GL_SPECULAR, floatBuffer );
            
            // Set Ambient component of this light to 0
            gl.glLightfv( glLight, GL.GL_AMBIENT, blackColorBuffer );
            
            if ( transform == null )
                tmpPoint.set( pLight.getLocation() );
            else
                transform.transform( pLight.getLocation(), tmpPoint );
            
            floatBuffer.clear();
            floatBuffer.put( tmpPoint.getX() );
            floatBuffer.put( tmpPoint.getY() );
            floatBuffer.put( tmpPoint.getZ() + 0.00001f ); // Workaround to prevent halfVector randomness
            floatBuffer.put( 1f );
            floatBuffer.rewind();
            gl.glLightfv( glLight, GL.GL_POSITION, floatBuffer );
            
            final Tuple3f att = pLight.getAttenuation();
            
            gl.glLightf( glLight, GL.GL_CONSTANT_ATTENUATION, att.getX() );
            gl.glLightf( glLight, GL.GL_LINEAR_ATTENUATION, att.getY() );
            gl.glLightf( glLight, GL.GL_QUADRATIC_ATTENUATION, att.getZ() );
            
            if ( light instanceof SpotLight )
            {
                final SpotLight sLight = (SpotLight)pLight;
                
                if ( transform == null )
                    tmpVector.set( sLight.getDirection() );
                else
                    transform.getMatrix4f().transform( sLight.getDirection(), tmpVector );
                
                floatBuffer.clear();
                floatBuffer.put( tmpVector.getX() );
                floatBuffer.put( tmpVector.getY() );
                floatBuffer.put( tmpVector.getZ() );
                floatBuffer.put( 1f );
                floatBuffer.rewind();
                gl.glLightfv( glLight, GL.GL_SPOT_DIRECTION, floatBuffer );
                gl.glLightf( glLight, GL.GL_SPOT_EXPONENT, sLight.getConcentration() );
                gl.glLightf( glLight, GL.GL_SPOT_CUTOFF, sLight.getSpreadAngleDeg() );
            }
            else
            {
                gl.glLightf( glLight, GL.GL_SPOT_EXPONENT, 0f );
                gl.glLightf( glLight, GL.GL_SPOT_CUTOFF, 180f );
            }
            
            if ( !statesCache.enabled || !statesCache.lightEnabled[ lightIndex ] )
            {
                gl.glEnable( glLight );
                statesCache.lightEnabled[ lightIndex ] = true;
            }
        }
        
        return ( true );
    }
    
    private static final Transform3D getTrackedNodeTransform( final Light light )
    {
        if ( light instanceof PointLight )
        {
            final PointLight pLight = (PointLight)light;
            
            if ( pLight.getTrackedNode() != null )
            {
                return ( pLight.getTrackedNode().getWorldTransform() );
            }
        }
        else if ( light instanceof DirectionalLight )
        {
            final DirectionalLight dLight = (DirectionalLight)light;
            
            if ( dLight.getTrackedNode() != null )
            {
                return ( dLight.getTrackedNode().getWorldTransform() );
            }
        }
        
        return ( null );
    }
    
    public void apply( RenderAtom< ? > atom, StateUnit stateUnit, Object glObj, CanvasPeer canvasPeer, RenderPeer renderPeer, OpenGLCapabilities glCaps, View view, OpenGLStatesCache statesCache, RenderOptions options, long nanoTime, long nanoStep, RenderMode renderMode, long frameId )
    {
        ProfileTimer.startProfile( X3DLog.LOG_CHANNEL, "LightingStateUnitPeer::apply()" );
        
        final GL gl = (GL)glObj;
        LightingStateUnit ls = (LightingStateUnit)stateUnit;
        
        // enable and set all the lights
        
        int lightIndex = 0;
        
        final boolean hasLights = ( ( ls.numLights() > 0 ) || ( view.getAttachedLight() != null ) );
        
        if ( ( renderMode == RenderMode.NORMAL ) && hasLights )
        {
            gl.glPushMatrix();
            gl.glLoadMatrixf( _SG_PrivilegedAccess.getFloatBuffer( view.getModelViewTransform( false ), true ) );
            
            for ( int i = 0; i < ls.numLights(); i++ )
            {
                final Light light = ls.getLight( i );
                
                if ( shadeLight( gl, statesCache, light, getTrackedNodeTransform( light ), lightIndex ) )
                {
                    lightIndex++;
                }
            }
            
            if ( shadeLight( gl, statesCache, view.getAttachedLight(), view.getTransform(), lightIndex ) )
            {
                lightIndex++;
            }
        }
        
        // disable all the other lights
        
        for ( int i = lightIndex; i <= 7; i++ )
        {
            if ( !statesCache.enabled || statesCache.lightEnabled[ i ] )
            {
                gl.glDisable( getGlLight( i ) );
                statesCache.lightEnabled[ i ] = false;
            }
        }
        
        if ( ( renderMode == RenderMode.NORMAL ) && hasLights )
        {
            gl.glPopMatrix();
        }
        
        ProfileTimer.endProfile();
    }
}

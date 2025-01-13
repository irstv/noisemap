/**
 * NoiseModelling is a library capable of producing noise maps. It can be freely used either for research and education, as well as by experts in a professional use.
 * <p>
 * NoiseModelling is distributed under GPL 3 license. You can read a copy of this License in the file LICENCE provided with this software.
 * <p>
 * Official webpage : http://noise-planet.org/noisemodelling.html
 * Contact: contact@noise-planet.org
 */

package org.noise_planet.noisemodelling.pathfinder.delaunay;

import org.tinfour.common.SimpleTriangle;

import java.util.ArrayList;
import java.util.function.Consumer;

/**
 * A triangle built from the combination of the 3 vertices index.
 * 
 * @author Nicolas Fortin
 */
public class Triangle {
	private int a = 0;
	private int b = 0;
	private int c = 0;
	private int attribute =-1;

	public int getA() {
		return a;
	}

	public int get(int id) {
		switch (id) {
		case 0:
			return a;
		case 1:
			return b;
		default:
			return c;
		}
	}
        public int getAttribute(){
                return this.attribute;
        }
        

	public void set(int id,int index) {
		switch (id) {
		case 0:
			a=index;
			break;
		case 1:
			b=index;
			break;
		default:
			c=index;
		}
	}

	public void setA(int a) {
		this.a = a;
	}

	public int getB() {
		return b;
	}

	public void setB(int b) {
		this.b = b;
	}

	public int getC() {
		return c;
	}

	public void setC(int c) {
		this.c = c;
	}

	public Triangle(int a, int b, int c, int attribute) {
		super();
		this.a = a;
		this.b = b;
		this.c = c;
                this.attribute = attribute;
	}
        
        public Triangle(int a, int b, int c) {
		super();
		this.a = a;
		this.b = b;
		this.c = c;
             
	}

	public static class TriangleBuilder implements Consumer<SimpleTriangle> {
		ArrayList<SimpleTriangle> triangles;

		public TriangleBuilder(ArrayList<SimpleTriangle> triangles) {
			this.triangles = triangles;
		}

		@Override
		public void accept(SimpleTriangle triangle) {
			triangles.add(triangle);
		}
	}
}

/**
 * NoiseModelling is a library capable of producing noise maps. It can be freely used either for research and education, as well as by experts in a professional use.
 * <p>
 * NoiseModelling is distributed under GPL 3 license. You can read a copy of this License in the file LICENCE provided with this software.
 * <p>
 * Official webpage : http://noise-planet.org/noisemodelling.html
 * Contact: contact@noise-planet.org
 */

package org.noise_planet.noisemodelling.pathfinder.delaunay;

/**
 * Throwed delaunay error.
 * @author Nicolas Fortin
 */
public class LayerDelaunayError extends Exception {
	private static final long serialVersionUID = 1L;

	// error code saving
	String errorMessage;

	public LayerDelaunayError(String ErrorMsg) {
		super();
		errorMessage = ErrorMsg;
	}

        public LayerDelaunayError(Throwable thrwbl) {
            super(thrwbl);
        }

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.lang.Throwable#getMessage()
	 */
	@Override
	public String getMessage() {
		return errorMessage;
	}
}

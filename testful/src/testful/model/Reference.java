/*
 * TestFul - http://code.google.com/p/testful/
 * Copyright (C) 2010  Matteo Miraz
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package testful.model;

import java.io.Serializable;

public class Reference implements Serializable, Comparable<Reference> {

	private static final long serialVersionUID = 2679593062737380911L;

	/** The type of the reference */
	private final Clazz clazz;
	/** The id of the reference, within the same type (e.g., the 1st Integer) */
	private final int pos;
	/** The id of the reference */
	private final int id;

	/**
	 * The correct visibility is package-only.
	 * However, since this constructor must be invoked by classes (in the package testful.model)
	 * loaded by different class-loaders, this constructor has a public visibility.
	 * @param clazz the type of the reference
	 * @param pos the position of the reference
	 * @param id the id of the reference
	 */
	public Reference(Clazz clazz, int pos, int id) {
		this.clazz = clazz;
		this.pos = pos;
		this.id = id;
	}

	public Clazz getClazz() {
		return clazz;
	}

	public int getPos() {
		return pos;
	}

	public int getId() {
		return id;
	}

	@Override
	public String toString() {
		return clazz.getClassName().replace('.', '_') + "_" + pos;
	}

	@Override
	public int hashCode() {
		return id * 31;
	}

	@Override
	public boolean equals(Object obj) {
		if(this == obj) return true;
		if(obj == null) return false;
		if(!(obj instanceof Reference)) return false;

		Reference other = (Reference) obj;
		return id == other.id && pos == other.pos && clazz.equals(other.clazz);
	}

	/* (non-Javadoc)
	 * @see java.lang.Comparable#compareTo(java.lang.Object)
	 */
	@Override
	public int compareTo(Reference o) {
		return id - o.id;
	}
}

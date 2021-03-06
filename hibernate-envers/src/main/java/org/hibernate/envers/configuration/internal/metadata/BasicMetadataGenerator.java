/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * Copyright (c) 2008, 2013, Red Hat Inc. or third-party contributors as
 * indicated by the @author tags or express copyright attribution
 * statements applied by the authors.  All third-party contributions are
 * distributed under license by Red Hat Inc.
 *
 * This copyrighted material is made available to anyone wishing to use, modify,
 * copy, or redistribute it subject to the terms and conditions of the GNU
 * Lesser General Public License, as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU Lesser General Public License
 * for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this distribution; if not, write to:
 * Free Software Foundation, Inc.
 * 51 Franklin Street, Fifth Floor
 * Boston, MA  02110-1301  USA
 */
package org.hibernate.envers.configuration.internal.metadata;

import java.util.Properties;

import org.hibernate.envers.configuration.internal.metadata.reader.PropertyAuditingData;
import org.hibernate.envers.internal.entities.mapper.SimpleMapperBuilder;
import org.hibernate.mapping.SimpleValue;
import org.hibernate.mapping.Value;
import org.hibernate.type.BasicType;
import org.hibernate.type.CustomType;
import org.hibernate.type.EnumType;
import org.hibernate.type.SerializableToBlobType;
import org.hibernate.type.Type;
import org.hibernate.usertype.DynamicParameterizedType;

import org.dom4j.Element;

/**
 * Generates metadata for basic properties: immutable types (including enums).
 *
 * @author Adam Warski (adam at warski dot org)
 */
public final class BasicMetadataGenerator {
	@SuppressWarnings({"unchecked"})
	boolean addBasic(
			Element parent, PropertyAuditingData propertyAuditingData,
			Value value, SimpleMapperBuilder mapper, boolean insertable, boolean key) {
		final Type type = value.getType();

		if ( type instanceof BasicType
				|| type instanceof SerializableToBlobType
				|| "org.hibernate.type.PrimitiveByteArrayBlobType".equals( type.getClass().getName() ) ) {
			if ( parent != null ) {
				final boolean addNestedType = (value instanceof SimpleValue)
						&& ((SimpleValue) value).getTypeParameters() != null;

				String typeName = type.getName();
				if ( typeName == null ) {
					typeName = type.getClass().getName();
				}

				final Element propMapping = MetadataTools.addProperty(
						parent,
						propertyAuditingData.getName(),
						addNestedType ? null : typeName,
						propertyAuditingData.isForceInsertable() || insertable,
						key
				);
				MetadataTools.addColumns( propMapping, value.getColumnIterator() );

				if ( addNestedType ) {
					final Properties typeParameters = ((SimpleValue) value).getTypeParameters();
					final Element typeMapping = propMapping.addElement( "type" );
					typeMapping.addAttribute( "name", typeName );

					if ( "org.hibernate.type.EnumType".equals( typeName ) ) {
						// Proper handling of enumeration type
						mapEnumerationType( typeMapping, type, typeParameters );
					}
					else {
						// By default copying all Hibernate properties
						for ( Object object : typeParameters.keySet() ) {
							final String keyType = (String) object;
							final String property = typeParameters.getProperty( keyType );

							if ( property != null ) {
								typeMapping.addElement( "param" ).addAttribute( "name", keyType ).setText( property );
							}
						}
					}
				}
			}

			// A null mapper means that we only want to add xml mappings
			if ( mapper != null ) {
				mapper.add( propertyAuditingData.getPropertyData() );
			}
		}
		else {
			return false;
		}

		return true;
	}

	private void mapEnumerationType(Element parent, Type type, Properties parameters) {
		if ( parameters.getProperty( EnumType.ENUM ) != null ) {
			parent.addElement( "param" )
					.addAttribute( "name", EnumType.ENUM )
					.setText( parameters.getProperty( EnumType.ENUM ) );
		}
		else {
			parent.addElement( "param" ).addAttribute( "name", EnumType.ENUM ).setText(
					type.getReturnedClass()
							.getName()
			);
		}
		if ( parameters.getProperty( EnumType.NAMED ) != null ) {
			parent.addElement( "param" ).addAttribute( "name", EnumType.NAMED ).setText(
					parameters.getProperty(
							EnumType.NAMED
					)
			);
		}
		else if ( parameters.get( DynamicParameterizedType.XPROPERTY ) != null ) {
			// Case of annotations.
			parent.addElement( "param" ).addAttribute( "name", EnumType.NAMED )
					.setText( "" + !((EnumType) ((CustomType) type).getUserType()).isOrdinal() );
		}
		// Otherwise we assume that the choice between ordinal and named representation has been omitted.
		// Call to EnumType#isOrdinal() would always return the default Types.INTEGER. We let Hibernate
		// to choose the proper strategy during runtime.
	}

	@SuppressWarnings({"unchecked"})
	boolean addManyToOne(
			Element parent,
			PropertyAuditingData propertyAuditingData,
			Value value,
			SimpleMapperBuilder mapper) {
		final Type type = value.getType();

		// A null mapper occurs when adding to composite-id element
		final Element manyToOneElement = parent.addElement( mapper != null ? "many-to-one" : "key-many-to-one" );
		manyToOneElement.addAttribute( "name", propertyAuditingData.getName() );
		manyToOneElement.addAttribute( "class", type.getName() );
		MetadataTools.addColumns( manyToOneElement, value.getColumnIterator() );

		// A null mapper means that we only want to add xml mappings
		if ( mapper != null ) {
			mapper.add( propertyAuditingData.getPropertyData() );
		}

		return true;
	}
}

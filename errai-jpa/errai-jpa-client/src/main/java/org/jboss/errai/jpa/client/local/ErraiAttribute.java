package org.jboss.errai.jpa.client.local;

import javax.persistence.CascadeType;
import javax.persistence.metamodel.Attribute;

/**
 * Extends the JPA Attribute interface with methods required by Errai
 * persistence but missing from the JPA metamodel. Most importantly, this
 * interface provides methods for reading and writing the attribute value.
 *
 * @param <X>
 *          The type containing the represented attribute
 * @param <Y>
 *          The type of the represented attribute
 * @author Jonathan Fuerth <jfuerth@gmail.com>
 */
public interface ErraiAttribute<X, Y> extends Attribute<X, Y> {

  /**
   * Returns true if this attribute cascades the given type of operation.
   *
   * @return true if operations of type {@code cascadeType} should be cascaded
   *         to this attribute from its owning entity.
   */
  public boolean cascades(CascadeType cascadeType);

  /**
   * Retrieves the value of this attribute from the given entity instance.
   *
   * @param entityInstance
   *          The entity to retrieve the entity value from. The type of this
   *          argument must be assignable to the declaring entity's type
   *          (returned by {@link #getDeclaringType()}).
   * @return The value of this attribute on the given entity instance.
   */
  public Y get(X entityInstance);

  /**
   * Sets the value of this attribute on the given entity instance.
   *
   * @param entityInstance
   *          The entity to set this attribute value on. The type of this
   *          argument must be assignable to the declaring entity's type
   *          (returned by {@link #getDeclaringType()}).
   * @param value
   *          The value to set the attribute to.
   */
  public void set(X entityInstance, Y value);

  /**
   * Returns the type of this attribute, its owning entity type, and the attribute's name.
   */
  @Override
  public String toString();
}

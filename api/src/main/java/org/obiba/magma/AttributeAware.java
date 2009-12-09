package org.obiba.magma;

import java.util.List;
import java.util.Locale;

public interface AttributeAware {

  /**
   * Returns true if this instance has at least one {@code Attribute} (with any name).
   * @return true when this instance has at least one {@code Attribute}
   */
  public boolean hasAttributes();

  /**
   * Returns true if this instance has at least one {@code Attribute} with the specified name.
   * @return true when this instance has at least one {@code Attribute} with the specified name; false otherwise.
   */
  public boolean hasAttribute(String name);

  /**
   * Returns the first attribute associated with the specified name. Note that multiple instances of {@code Attribute}
   * can have the same name. This method will always return the first one.
   * @param name
   * @return
   */
  public Attribute getAttribute(String name) throws NoSuchAttributeException;

  public Attribute getAttribute(String name, Locale locale) throws NoSuchAttributeException;

  /**
   * Equivalent to calling
   * 
   * <pre>
   * getAttribute(name).getValue()
   * 
   * <pre>
   */
  public Value getAttributeValue(String name) throws NoSuchAttributeException;

  /**
   * Equivalent to calling
   * 
   * <pre>
   * getAttribute(name).getValue().toString()
   * 
   * <pre>
   */
  public String getAttributeStringValue(String name) throws NoSuchAttributeException;

  /**
   * Returns the list of attributes associated with the specified name.
   * @param name the key of the attributes to return
   * @return
   * @throws NoSuchAttributeException when no attribute exists for the specified key
   */
  public List<Attribute> getAttributes(String name) throws NoSuchAttributeException;

  public List<Attribute> getAttributes();

}

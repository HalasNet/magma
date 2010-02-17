package org.obiba.magma.datasource.hibernate.domain;

import java.util.ArrayList;
import java.util.List;

import javax.persistence.CascadeType;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.OneToMany;
import javax.persistence.Table;
import javax.persistence.UniqueConstraint;

import org.hibernate.annotations.IndexColumn;
import org.hibernate.annotations.Type;
import org.hibernate.annotations.TypeDef;
import org.obiba.magma.ValueType;
import org.obiba.magma.Variable;
import org.obiba.magma.datasource.hibernate.domain.attribute.AbstractAttributeAwareEntity;

@Entity
@Table(name = "variable", uniqueConstraints = { @UniqueConstraint(columnNames = { "value_table_id", "name" }) })
@TypeDef(name = "value_type", typeClass = ValueTypeHibernateType.class)
public class VariableState extends AbstractAttributeAwareEntity {

  private static final long serialVersionUID = 1L;

  @Column(nullable = false)
  private String name;

  @ManyToOne(optional = false)
  @JoinColumn(name = "value_table_id")
  private ValueTableState valueTable;

  @OneToMany(cascade = CascadeType.ALL)
  @IndexColumn(name = "category_index")
  private List<CategoryState> categories;

  @Column(nullable = false)
  private String entityType;

  private String mimeType;

  private String occurrenceGroup;

  private String referencedEntityType;

  private String unit;

  @Type(type = "value_type")
  private ValueType valueType;

  @Column(nullable = false)
  private boolean repeatable;

  private Integer pos;

  public VariableState() {
    super();
  }

  public VariableState(ValueTableState valueTable, Variable variable) {
    super();
    this.valueTable = valueTable;
    this.name = variable.getName();
    this.entityType = variable.getEntityType();
    this.valueType = variable.getValueType();
    this.mimeType = variable.getMimeType();
    this.occurrenceGroup = variable.getOccurrenceGroup();
    this.referencedEntityType = variable.getReferencedEntityType();
    this.unit = variable.getUnit();
    this.repeatable = variable.isRepeatable();
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public ValueTableState getValueTable() {
    return valueTable;
  }

  public void setValueTable(ValueTableState valueTable) {
    this.valueTable = valueTable;
  }

  public String getEntityType() {
    return entityType;
  }

  public void setEntityType(String entityType) {
    this.entityType = entityType;
  }

  public String getMimeType() {
    return mimeType;
  }

  public void setMimeType(String mimeType) {
    this.mimeType = mimeType;
  }

  public String getOccurrenceGroup() {
    return occurrenceGroup;
  }

  public void setOccurrenceGroup(String occurrenceGroup) {
    this.occurrenceGroup = occurrenceGroup;
  }

  public String getReferencedEntityType() {
    return referencedEntityType;
  }

  public void setReferencedEntityType(String referencedEntityType) {
    this.referencedEntityType = referencedEntityType;
  }

  public String getUnit() {
    return unit;
  }

  public void setUnit(String unit) {
    this.unit = unit;
  }

  public ValueType getValueType() {
    return valueType;
  }

  public void setValueType(ValueType valueType) {
    this.valueType = valueType;
  }

  public Integer getPosition() {
    return pos;
  }

  public void setPosition(Integer pos) {
    this.pos = pos;
  }

  public void setRepeatable(boolean repeatable) {
    this.repeatable = repeatable;
  }

  public boolean isRepeatable() {
    return repeatable;
  }

  public List<CategoryState> getCategories() {
    return categories != null ? categories : (categories = new ArrayList<CategoryState>());
  }

  public void addCategory(CategoryState state) {
    state.setCategoryIndex(getCategories().size());
    getCategories().add(state);
  }

  public CategoryState getCategory(String name) {
    for(CategoryState state : getCategories()) {
      if(name.equals(state.getName())) {
        return state;
      }
    }
    return null;
  }

}

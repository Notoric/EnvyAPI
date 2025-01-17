package com.envyful.api.player.save.impl;

import com.envyful.api.concurrency.UtilConcurrency;
import com.envyful.api.concurrency.UtilLogger;
import com.envyful.api.database.Database;
import com.envyful.api.player.PlayerManager;
import com.envyful.api.player.attribute.Attribute;
import com.envyful.api.player.save.AbstractSaveManager;
import com.envyful.api.player.save.SaveHandlerFactory;
import com.envyful.api.player.save.VariableSaveHandler;
import com.envyful.api.player.save.attribute.ColumnData;
import com.envyful.api.player.save.attribute.Queries;
import com.envyful.api.player.save.attribute.SaveHandler;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class SQLSaveManager<T> extends AbstractSaveManager<T> {

    private final Database database;
    protected final Map<Class<? extends Attribute<?>>, SQLAttributeData> registeredSqlAttributeData = Maps.newConcurrentMap();

    public SQLSaveManager(PlayerManager<?, ?> playerManager, Database database) {
        super(playerManager);
        this.database = database;
    }

    @Override
    public CompletableFuture<List<Attribute<?>>> loadData(UUID uuid) {
        if (this.registeredAttributes.isEmpty()) {
            return CompletableFuture.completedFuture(Collections.emptyList());
        }

        List<Attribute<?>> attributes = Lists.newArrayList();
        List<CompletableFuture<Attribute<?>>> loadTasks = Lists.newArrayList();

        for (Map.Entry<Class<? extends Attribute<?>>, AttributeData<?, ?>> entry : this.registeredAttributes.entrySet()) {
            AttributeData<?, ?> value = entry.getValue();
            Attribute<?> attribute = value.getConstructor().get();

            loadTasks.add(attribute.getId(uuid).thenApply(o -> {
                if (o == null) {
                    return null;
                }

                if (attribute.isShared()) {
                    Attribute<?> sharedAttribute = this.getSharedAttribute((Class<? extends Attribute<?>>) attribute.getClass(), o);

                    if (sharedAttribute == null) {
                        sharedAttribute = this.readData(attribute,
                                this.registeredSqlAttributeData.get(entry.getKey()));
                        this.addSharedAttribute(o, sharedAttribute);
                    }

                    return sharedAttribute;
                } else {
                    return this.readData(attribute,
                            this.registeredSqlAttributeData.get(entry.getKey()));
                }
            }).whenComplete((loaded, throwable) -> {
                if (loaded != null) {
                    attributes.add(loaded);
                } else if (throwable != null) {
                    UtilLogger.logger().ifPresent(logger -> logger.error("Error when loading attribute " + entry.getKey().getName(), throwable));
                }
            }).exceptionally(throwable -> {
                UtilLogger.logger().ifPresent(logger -> logger.error("Error when loading attribute " + entry.getKey().getName(), throwable));
                return null;
            }));
        }

        return CompletableFuture.allOf(loadTasks.toArray(new CompletableFuture[0])).thenApply(unused -> attributes);
    }

    protected Attribute<?> readData(
            Attribute<?> original,
            SQLAttributeData sqlAttributeData
    ) {
        try (Connection connection = this.database.getConnection();
             PreparedStatement preparedStatement =
                     connection.prepareStatement(sqlAttributeData.getQueries().loadQuery())) {
            Field[] fields = sqlAttributeData.getFieldsPositions().get(sqlAttributeData.getQueries().loadQuery());

            for (int i = 0; i < fields.length; i++) {
                preparedStatement.setObject(i, fields[i].get(original));
            }

            ResultSet resultSet = preparedStatement.executeQuery();

            if (!resultSet.next()) {
                return original;
            }

            for (Map.Entry<Field, FieldData> fieldData : sqlAttributeData.getFieldData().entrySet()) {
                FieldData data = fieldData.getValue();

                if (data.getSaveHandler() != null) {
                    fieldData.getKey().set(original,
                            data.getSaveHandler().invert(
                                    resultSet.getString(fieldData.getValue().getName())));
                } else {
                    fieldData.getKey().set(original, resultSet.getObject(fieldData.getValue().getName()));
                }
            }
        } catch (SQLException | IllegalAccessException e) {
            e.printStackTrace();
        }

        return original;
    }

    @Override
    public <A extends Attribute<?>, B> CompletableFuture<A> loadAttribute(Class<? extends A> attributeClass, B id) {
        if (id == null) {
            return CompletableFuture.completedFuture(null);
        }

        return CompletableFuture.supplyAsync(() -> {
            AttributeData<?, A> attributeData = (AttributeData<?, A>) this.registeredAttributes.get(attributeClass);
            A attribute = attributeData.getConstructor().get();

            if (attribute.isShared()) {
                A sharedAttribute = (A) this.getSharedAttribute(attributeClass, id);

                if (sharedAttribute == null) {
                    sharedAttribute = (A) this.readData(attribute,
                            this.registeredSqlAttributeData.get(attributeClass));
                    this.addSharedAttribute(id, sharedAttribute);
                }

                return sharedAttribute;
            } else {
                return (A) this.readData(attribute,
                        this.registeredSqlAttributeData.get(attributeClass));
            }
        }, UtilConcurrency.SCHEDULED_EXECUTOR_SERVICE);
    }

    @Override
    public void saveData(UUID player, Attribute<?> attribute) {
        SQLAttributeData sqlAttributeData = this.registeredSqlAttributeData.get(attribute.getClass());

        try (Connection connection = this.database.getConnection();
             PreparedStatement preparedStatement = connection.prepareStatement(sqlAttributeData.getQueries().updateQuery())) {
            Field[] fieldPositions = sqlAttributeData.getFieldsPositions().get(sqlAttributeData.getQueries().updateQuery());

            for (int i = 0; i < fieldPositions.length; i++) {
                Field fieldPosition = fieldPositions[i];

                FieldData fieldData = sqlAttributeData.getFieldData().get(fieldPosition);

                if (fieldData.getSaveHandler() != null) {
                    preparedStatement.setString(i, fieldData.getSaveHandler().convert(fieldPosition.get(attribute)));
                } else {
                    preparedStatement.setObject(i, fieldPosition.get(attribute));
                }
            }

            preparedStatement.executeUpdate();
        } catch (SQLException | IllegalAccessException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void registerAttribute(Class<? extends Attribute<?>> attribute) {
        Map<Field, FieldData> fieldData = this.getFieldData(attribute);
        Queries queries = attribute.getAnnotation(Queries.class);

        if (queries == null) {
            return;
        }

        Map<String, Field[]> fieldsPositions = ImmutableMap.of(
                queries.loadQuery(), this.getFieldPositions(queries.loadQuery(), fieldData),
                queries.updateQuery(), this.getFieldPositions(queries.updateQuery(), fieldData)
        );

        super.registerAttribute(attribute);
        this.registeredSqlAttributeData.put(attribute, new SQLAttributeData(queries, fieldData, fieldsPositions));
    }

    private Map<Field, FieldData> getFieldData(Class<? extends Attribute<?>> attribute) {
        Map<Field, FieldData> fieldData = Maps.newHashMap();

        for (Field declaredField : attribute.getDeclaredFields()) {
            if (Modifier.isTransient(declaredField.getModifiers())) {
                continue;
            }

            ColumnData columnData = declaredField.getAnnotation(ColumnData.class);
            SaveHandler saveHandler = declaredField.getAnnotation(SaveHandler.class);
            String name;
            VariableSaveHandler<?> variableSaveHandler = null;

            if (columnData == null) {
                name = this.calculateColumnName(declaredField);
            } else {
                name = columnData.value();
            }

            if (saveHandler != null) {
                variableSaveHandler = SaveHandlerFactory.getSaveHandler(saveHandler.value());
            }

            fieldData.put(declaredField, new FieldData(declaredField, name, variableSaveHandler));
        }

        return fieldData;
    }

    private Field[] getFieldPositions(String query, Map<Field, FieldData> fieldData) {
        List<Field> indexes = Lists.newArrayList();

        for (String s : query.split(" ")) {
            for (Map.Entry<Field, FieldData> fieldStringEntry : fieldData.entrySet()) {
                FieldData parameter = fieldStringEntry.getValue();

                if (s.equals(parameter.getName()) || s.startsWith(parameter.getName()) || s.endsWith(parameter.getName()) || s.contains(parameter.getName())) {
                    indexes.add(fieldStringEntry.getKey());
                }
            }
        }

        return indexes.toArray(new Field[0]);
    }

    private String calculateColumnName(Field field) {
        String name = field.getName();
        StringBuilder newName = new StringBuilder();

        for (char c : name.toCharArray()) {
            if (Character.isSpaceChar(c)) {
                newName.append("_");
            } else if(Character.isUpperCase(c)) {
                newName.append("_").append(Character.toLowerCase(c));
            } else {
                newName.append(c);
            }
        }

        return newName.toString();
    }

    public static class SQLAttributeData {

        private final Queries queries;
        private final Map<Field, FieldData> fieldData;
        private final Map<String, Field[]> fieldsPositions;

        public SQLAttributeData(Queries queries, Map<Field, FieldData> fieldData, Map<String, Field[]> fieldsPositions) {
            this.queries = queries;
            this.fieldData = fieldData;
            this.fieldsPositions = fieldsPositions;
        }

        public Queries getQueries() {
            return this.queries;
        }

        public Map<Field, FieldData> getFieldData() {
            return this.fieldData;
        }

        public Map<String, Field[]> getFieldsPositions() {
            return this.fieldsPositions;
        }
    }

    public static class FieldData {

        private final Field field;
        private final String name;
        private final VariableSaveHandler<?> saveHandler;

        public FieldData(Field field, String name, VariableSaveHandler<?> saveHandler) {
            this.field = field;
            this.name = name;
            this.saveHandler = saveHandler;
        }

        public Field getField() {
            return this.field;
        }

        public String getName() {
            return this.name;
        }

        public VariableSaveHandler<?> getSaveHandler() {
            return this.saveHandler;
        }
    }
}

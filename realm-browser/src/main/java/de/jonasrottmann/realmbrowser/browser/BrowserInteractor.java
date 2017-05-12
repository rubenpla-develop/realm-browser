package de.jonasrottmann.realmbrowser.browser;

import android.content.Context;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.RestrictTo;

import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.util.ArrayList;
import java.util.List;

import de.jonasrottmann.realmbrowser.basemvp.BaseInteractorImpl;
import de.jonasrottmann.realmbrowser.helper.RealmHolder;
import de.jonasrottmann.realmbrowser.helper.RealmPreferences;
import de.jonasrottmann.realmbrowser.helper.Utils;
import io.realm.DynamicRealm;
import io.realm.DynamicRealmObject;
import io.realm.RealmModel;
import io.realm.RealmObject;
import io.realm.RealmObjectSchema;
import timber.log.Timber;

@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
class BrowserInteractor extends BaseInteractorImpl<BrowserContract.Presenter> implements BrowserContract.Interactor {

    private Class<? extends RealmModel> realmModelClass = null;
    private DynamicRealm dynamicRealm;
    private List<Field> fields;
    private ArrayList<Integer> selectedFieldIndices;

    BrowserInteractor(BrowserContract.Presenter presenter) {
        super(presenter);
    }

    //region InteractorInput
    @Override
    public void requestForContentUpdate(@NonNull Context context, @NonNull DynamicRealm dynamicRealm, @Nullable Class<? extends RealmModel> modelClass) {
        this.realmModelClass = modelClass;
        this.dynamicRealm = dynamicRealm;

        getPresenter().updateWithFABVisibility(this.realmModelClass != null);

        if (!dynamicRealm.isClosed() && this.realmModelClass != null) {
            getPresenter().updateWithRealmObjects(dynamicRealm.where(this.realmModelClass.getSimpleName()).findAll());
        } else if (!dynamicRealm.isClosed()) {
            DynamicRealmObject dynamicRealmObject = RealmHolder.getInstance().getObject();
            Field field = RealmHolder.getInstance().getField();
            getPresenter().updateWithRealmObjects(dynamicRealmObject.getList(field.getName()));
            if (Utils.isParametrizedField(field)) {
                ParameterizedType pType = (ParameterizedType) field.getGenericType();
                Class<?> pTypeClass = (Class<?>) pType.getActualTypeArguments()[0];
                this.realmModelClass = (Class<? extends RealmObject>) pTypeClass;
            }
        }

        // Update Title
        getPresenter().updateWithTitle(String.format("%s", this.realmModelClass.getSimpleName()));

        // Update Fields
        fields = getFieldsList(dynamicRealm, this.realmModelClass);
        selectedFieldIndices = new ArrayList<>();
        for (int i = 0; i < fields.size(); i++) {
            if (i < 3) selectedFieldIndices.add(i);
        }
        updateSelectedFields();

        // Wrap
        getPresenter().updateWithTextWrap(new RealmPreferences(context).shouldWrapText());
    }

    @Override
    public void onWrapTextOptionChanged(boolean wrapText, @NonNull Context context) {
        RealmPreferences realmPreferences = new RealmPreferences(context);
        realmPreferences.setShouldWrapText(wrapText);
        getPresenter().updateWithTextWrap(realmPreferences.shouldWrapText());
    }

    @Override
    public void onNewObjectSelected() {
        if (this.realmModelClass != null) {
            getPresenter().showNewObjectActivity(this.realmModelClass);
        } else {
            throw new IllegalStateException();
        }
    }

    @Override
    public void onRowSelected(@NonNull DynamicRealmObject realmObject) {
        RealmHolder.getInstance().setObject(realmObject);
        getPresenter().showObjectActivity(this.realmModelClass);
    }

    @Override
    public void onFieldSelectionChanged(int fieldIndex, boolean checked) {
        if (checked && !selectedFieldIndices.contains(fieldIndex)) {
            selectedFieldIndices.add(fieldIndex);
        } else if (selectedFieldIndices.contains(fieldIndex)) {
            selectedFieldIndices.remove((Integer) fieldIndex);
        }
        updateSelectedFields();
    }
    //endregion


    //region Helper
    @NonNull
    private static List<Field> getFieldsList(@NonNull DynamicRealm dynamicRealm, Class<? extends RealmModel> realmModelClass) {
        RealmObjectSchema schema = dynamicRealm.getSchema().get(realmModelClass.getSimpleName());
        ArrayList<Field> fieldsList = new ArrayList<>();
        for (String s : schema.getFieldNames()) {
            try {
                fieldsList.add(realmModelClass.getDeclaredField(s));
            } catch (NoSuchFieldException e) {
                Timber.d(e, "Initializing field map.");
            }
        }
        return fieldsList;
    }

    private void updateSelectedFields() {
        Integer[] selectedFieldIndicesArray = new Integer[selectedFieldIndices.size()];
        selectedFieldIndices.toArray(selectedFieldIndicesArray);
        getPresenter().updateWithFieldList(fields, selectedFieldIndicesArray);
    }
    //endregion
}

package com.proggroup.areasquarecalculator.fragments;

import android.app.Activity;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.app.ListFragment;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.Loader;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.ListView;

import com.proggroup.areasquarecalculator.R;
import com.proggroup.areasquarecalculator.api.LibraryContentAttachable;
import com.proggroup.areasquarecalculator.loaders.LoadCategoriesLoader;

import java.util.List;

public class SelectCategoryFragment extends ListFragment implements LoaderManager
        .LoaderCallbacks<List<String>> {

    private static final int LOAD_CATEGORIES_LOADER_ID = 0;

    @Override
    public void onAttach(Activity context) {
        super.onAttach(context);
        getLoaderManager().initLoader(LOAD_CATEGORIES_LOADER_ID, null, this);
    }

    @Override
    public Loader<List<String>> onCreateLoader(int id, Bundle bundle) {
        switch (id) {
            case LOAD_CATEGORIES_LOADER_ID:
                return new LoadCategoriesLoader();
        }
        return null;
    }

    @Override
    public void onLoadFinished(Loader<List<String>> loader, List<String> strings) {
        if(loader.getId() == LOAD_CATEGORIES_LOADER_ID && isAdded()) {
            Activity activity = getActivity();
            getView().setBackgroundColor(getResources().getColor(R.color.drawer_color));
            setListAdapter(new ArrayAdapter<>(activity, R.layout.item_select_category, R.id
                    .select_category_text, strings));
        }
    }

    @Override
    public void onListItemClick(ListView l, View v, int position, long id) {
        final Fragment newlyInsertedFragment;

        switch (position) {
            case 0:
                newlyInsertedFragment = new CalculateSquareAreaFragment();
                break;
            case 1:
                newlyInsertedFragment = new CalculatePpmSimpleFragment();
                break;
            default:
                newlyInsertedFragment = null;
        }

        Activity activity = getActivity();
        LibraryContentAttachable libraryContentAttachable = activity instanceof
                LibraryContentAttachable ? (LibraryContentAttachable) activity : null;

        if(newlyInsertedFragment != null && libraryContentAttachable != null) {
            FragmentManager fragmentManager = libraryContentAttachable.getSupportFragmentManager();

            int fragmentContainerId = libraryContentAttachable.getFragmentContainerId();

            fragmentManager.popBackStack(fragmentContainerId, FragmentManager
                    .POP_BACK_STACK_INCLUSIVE);

            FragmentTransaction transaction = fragmentManager.beginTransaction();
            transaction.replace(fragmentContainerId, newlyInsertedFragment);
            transaction.commit();

            libraryContentAttachable.getDrawerLayout().closeDrawer(activity.findViewById
                    (libraryContentAttachable.getLeftDrawerFragmentId()));
        }
    }

    @Override
    public void onLoaderReset(Loader<List<String>> loader) {

    }
}

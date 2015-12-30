package com.proggroup.areasquarecalculator.activities;

import android.annotation.TargetApi;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBar;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.View;
import android.widget.TextView;

import com.proggroup.areasquarecalculator.InterpolationCalculator;
import com.proggroup.areasquarecalculator.R;
import com.proggroup.areasquarecalculator.api.LibraryContentAttachable;
import com.proggroup.areasquarecalculator.fragments.CalculatePpmSimpleFragment;

public abstract class BaseAttachableActivity extends AppCompatActivity implements
        LibraryContentAttachable{

    public static final int LEFT_DRAWER_FRAGMENT_ID_UNDEFINED = -1;

    private DrawerLayout mDrawerLayout;
    private View mFragmentContainerView;
    private ActionBarDrawerToggle mDrawerToggle;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(getLayoutId());
        setupDrawer(getLeftDrawerFragmentId(), getDrawerLayout());

        Fragment fragment;
        FragmentManager manager = getSupportFragmentManager();

        int mainContainerId = getFragmentContainerId();

        if (savedInstanceState == null) {
            fragment = getFirstFragment();
        } else {
            fragment = manager.findFragmentById(mainContainerId);
        }
        manager.beginTransaction().replace(mainContainerId, fragment).commit();
    }

    @Override
    public void finish() {
        super.finish();
        InterpolationCalculator.getInstance().setPpmPoints(null);
        InterpolationCalculator.getInstance().setAvgSquarePoints(null);
    }

    public abstract int getLayoutId();

    public abstract Fragment getFirstFragment();

    private void setupDrawer(int fragmentId, DrawerLayout drawerLayout) {
        Toolbar toolbar = (Toolbar) findViewById(getToolbarId());

        toolbar.setTitle(R.string.app_name);

        setSupportActionBar(toolbar);

        ActionBar ab = getSupportActionBar();
        ab.setDisplayHomeAsUpEnabled(true);
        ab.setHomeButtonEnabled(true);
        ab.setDisplayShowHomeEnabled(true);
        ab.setDisplayShowTitleEnabled(true);
        ab.setDisplayUseLogoEnabled(false);
        ab.setDisplayShowCustomEnabled(true);

        ab.setCustomView(R.layout.toolbar);

        ((TextView)ab.getCustomView().findViewById(R.id.title)).setText(getString(R.string
                .app_name));

        if(fragmentId == LEFT_DRAWER_FRAGMENT_ID_UNDEFINED) {
            ab.setDisplayHomeAsUpEnabled(false);
            return;
        }

        mFragmentContainerView = findViewById(fragmentId);
        mDrawerLayout = drawerLayout;

        // set a custom shadow that overlays the main content when the drawer
        // opens
        mDrawerLayout.setDrawerShadow(R.drawable.drawer_shadow, GravityCompat.START);

        mDrawerToggle = new ActionBarDrawerToggle(this, mDrawerLayout,
                toolbar,
                R.string.drawer_cont_desc_open,
                R.string.drawer_cont_desc_close) {
            @TargetApi(Build.VERSION_CODES.HONEYCOMB)
            @Override
            public void onDrawerClosed(View drawerView) {
                super.onDrawerClosed(drawerView);
                invalidateOptionsMenu();
            }

            @TargetApi(Build.VERSION_CODES.HONEYCOMB)
            @Override
            public void onDrawerOpened(View drawerView) {
                super.onDrawerOpened(drawerView);
                invalidateOptionsMenu();
            }
        };

        mDrawerLayout.closeDrawer(mFragmentContainerView);

        mDrawerLayout.post(new Runnable() {
            @Override
            public void run() {
                mDrawerToggle.setDrawerIndicatorEnabled(true);
                mDrawerToggle.syncState();
            }
        });

        mDrawerLayout.setDrawerListener(mDrawerToggle);
    }
}

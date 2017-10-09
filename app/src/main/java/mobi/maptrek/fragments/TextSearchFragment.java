package mobi.maptrek.fragments;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ListFragment;
import android.content.Context;
import android.content.DialogInterface;
import android.content.res.Resources;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.ShapeDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.support.annotation.ColorInt;
import android.support.annotation.DrawableRes;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CursorAdapter;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import org.oscim.core.GeoPoint;

import at.grabner.circleprogress.CircleProgressView;
import mobi.maptrek.Configuration;
import mobi.maptrek.MapHolder;
import mobi.maptrek.MapTrek;
import mobi.maptrek.R;
import mobi.maptrek.maps.maptrek.MapTrekDatabaseHelper;
import mobi.maptrek.maps.maptrek.Tags;
import mobi.maptrek.util.HelperUtils;
import mobi.maptrek.util.StringFormatter;

public class TextSearchFragment extends ListFragment implements View.OnClickListener {
    public static final String ARG_LATITUDE = "lat";
    public static final String ARG_LONGITUDE = "lon";

    private static final int MSG_CREATE_FTS = 1;
    private static final int MSG_SEARCH = 2;

    private HandlerThread mBackgroundThread;
    private Handler mBackgroundHandler;
    private FragmentHolder mFragmentHolder;
    private MapHolder mMapHolder;

    private static final String[] columns = new String[]{"_id", "name", "kind", "lat", "lon"};

    private boolean mUpdating;
    private SQLiteDatabase mDatabase;
    private DataListAdapter mAdapter;
    private MatrixCursor mEmptyCursor = new MatrixCursor(columns);
    private GeoPoint mCoordinates;
    private CharSequence[] mKinds;
    private int mSelectedKind;

    private CircleProgressView mFtsWait;
    private TextView mMessage;
    private ImageButton mFilterButton;
    private View mSearchFooter;
    private String mText;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setRetainInstance(true);
        mBackgroundThread = new HandlerThread("SearchThread");
        mBackgroundThread.start();
        mBackgroundHandler = new Handler(mBackgroundThread.getLooper());
        mUpdating = false;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        final View rootView = inflater.inflate(R.layout.fragment_search_list, container, false);

        mFtsWait = (CircleProgressView) rootView.findViewById(R.id.ftsWait);
        mMessage = (TextView) rootView.findViewById(R.id.message);
        mFilterButton = (ImageButton) rootView.findViewById(R.id.filterButton);
        mFilterButton.setOnClickListener(this);
        mSearchFooter = rootView.findViewById(R.id.searchFooter);
        final EditText textEdit = (EditText) rootView.findViewById(R.id.textEdit);
        textEdit.requestFocus();

        textEdit.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                if (s.length() == 0) {
                    mAdapter.changeCursor(mEmptyCursor);
                    mText = null;
                    return;
                }
                mText = s.toString();
                search();
            }
        });

        return rootView;
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        Bundle arguments = getArguments();
        double latitude = arguments.getDouble(ARG_LATITUDE);
        double longitude = arguments.getDouble(ARG_LONGITUDE);

        if (savedInstanceState != null) {
            latitude = savedInstanceState.getDouble(ARG_LATITUDE);
            longitude = savedInstanceState.getDouble(ARG_LONGITUDE);
        }

        Activity activity = getActivity();

        mCoordinates = new GeoPoint(latitude, longitude);

        mDatabase = MapTrek.getApplication().getDetailedMapDatabase();

        mAdapter = new DataListAdapter(activity, mEmptyCursor, 0);
        setListAdapter(mAdapter);

        Resources resources = activity.getResources();
        mKinds = new CharSequence[Tags.kinds.length + 1];
        mKinds[0] = activity.getString(R.string.any);
        for (int i = 0; i < Tags.kinds.length; i++) {
            int id = resources.getIdentifier(Tags.kinds[i], "string", getActivity().getPackageName());
            mKinds[i + 1] = id != 0 ? resources.getString(id) : Tags.kinds[i];
        }

        if (mUpdating || !MapTrekDatabaseHelper.hasFullTextIndex(mDatabase)) {
            mSearchFooter.setVisibility(View.GONE);
            mFtsWait.spin();
            mFtsWait.setVisibility(View.VISIBLE);
            mMessage.setText(R.string.msgWaitForFtsTable);
            mMessage.setVisibility(View.VISIBLE);

            if (!mUpdating) {
                mUpdating = true;
                final Message m = Message.obtain(mBackgroundHandler, new Runnable() {
                    @Override
                    public void run() {
                        MapTrekDatabaseHelper.createFtsTable(mDatabase);
                        hideProgress();
                        mUpdating = false;
                    }
                });
                m.what = MSG_CREATE_FTS;
                mBackgroundHandler.sendMessage(m);
            } else {
                mBackgroundHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        hideProgress();
                    }
                });
            }
        } else {
            HelperUtils.showTargetedAdvice(getActivity(), Configuration.ADVICE_TEXT_SEARCH, R.string.advice_text_search, mSearchFooter, false);
        }
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);

        try {
            mMapHolder = (MapHolder) context;
        } catch (ClassCastException e) {
            throw new ClassCastException(context.toString() + " must implement MapHolder");
        }
        try {
            mFragmentHolder = (FragmentHolder) context;
        } catch (ClassCastException e) {
            throw new ClassCastException(context.toString() + " must implement FragmentHolder");
        }
    }

    @Override
    public void onDetach() {
        super.onDetach();
        mFragmentHolder = null;
        mMapHolder = null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mBackgroundThread.interrupt();
        mBackgroundHandler.removeCallbacksAndMessages(null);
        mBackgroundThread.quit();
        mBackgroundThread = null;
    }

    private void hideProgress() {
        Activity activity = getActivity();
        if (activity == null)
            return;
        activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mFtsWait.setVisibility(View.GONE);
                mFtsWait.stopSpinning();
                mMessage.setVisibility(View.GONE);
                mSearchFooter.setVisibility(View.VISIBLE);
                HelperUtils.showTargetedAdvice(getActivity(), Configuration.ADVICE_TEXT_SEARCH, R.string.advice_text_search, mSearchFooter, false);
            }
        });
    }

    @Override
    public void onClick(View view) {
        if (view != mFilterButton)
            return;
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        builder.setSingleChoiceItems(mKinds, mSelectedKind, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
                boolean changed = which != mSelectedKind;
                mSelectedKind = which;
                mFilterButton.setColorFilter(getActivity().getColor(mSelectedKind > 0 ? R.color.colorAccent : R.color.colorPrimaryDark));
                if (changed && mText != null)
                    search();
            }
        });
        AlertDialog dialog = builder.create();
        dialog.show();
    }

    private void search() {
        String[] words = mText.split(" ");
        for (int i = 0; i < words.length; i++) {
            if (words[i].length() > 2)
                words[i] = words[i] + "*";
        }
        final String match = TextUtils.join(" ", words);
        // SELECT * FROM "accounts" WHERE ("privileges" & 3) == 3;
        String kindFilter = "";
        if (mSelectedKind > 0) {
            int mask = 1 << (mSelectedKind + 2);
            kindFilter = " AND (kind & " + mask + ") == " + mask;
        }
        final String sql = "SELECT DISTINCT features.id AS _id, kind, lat, lon, names.name AS name FROM names_fts" +
                " INNER JOIN names ON (names_fts.docid = names.ref)" +
                " INNER JOIN feature_names ON (names.ref = feature_names.name)" +
                " INNER JOIN features ON (feature_names.id = features.id)" +
                " WHERE names_fts MATCH ? AND (lat != 0 OR lon != 0)" + kindFilter;
        //String sql = "SELECT feature_names.id AS _id, names.name FROM feature_names" +
        //        " INNER JOIN names ON (names.ref = feature_names.name)" +
        //        " WHERE feature_names.name IN (SELECT docid FROM names_fts WHERE names_fts MATCH ?)";
        mFilterButton.setImageResource(R.drawable.ic_hourglass_empty);
        mFilterButton.setColorFilter(getActivity().getColor(R.color.colorPrimaryDark));
        mFilterButton.setOnClickListener(null);
        final Message m = Message.obtain(mBackgroundHandler, new Runnable() {
            @Override
            public void run() {
                String[] selectionArgs = {match};
                final Cursor cursor = mDatabase.rawQuery(sql, selectionArgs);
                final Activity activity = getActivity();
                if (activity == null)
                    return;
                activity.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        mFilterButton.setImageResource(R.drawable.ic_filter);
                        mFilterButton.setColorFilter(activity.getColor(mSelectedKind > 0 ? R.color.colorAccent : R.color.colorPrimaryDark));
                        mFilterButton.setOnClickListener(TextSearchFragment.this);
                        mAdapter.changeCursor(cursor);
                    }
                });
            }
        });
        m.what = MSG_SEARCH;
        mBackgroundHandler.sendMessage(m);
    }

    private class DataListAdapter extends CursorAdapter {
        private LayoutInflater mInflater;

        DataListAdapter(Context context, Cursor cursor, int flags) {
            super(context, cursor, flags);
            mInflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        }

        @Override
        public View newView(Context context, Cursor cursor, ViewGroup parent) {
            View view = mInflater.inflate(R.layout.list_item_amenity, parent, false);
            if (view != null) {
                ItemHolder holder = new ItemHolder();
                holder.name = (TextView) view.findViewById(R.id.name);
                holder.distance = (TextView) view.findViewById(R.id.distance);
                holder.icon = (ImageView) view.findViewById(R.id.icon);
                holder.viewButton = (ImageView) view.findViewById(R.id.view);
                view.setTag(holder);
            }
            return view;
        }

        @Override
        public void bindView(View view, Context context, Cursor cursor) {
            ItemHolder holder = (ItemHolder) view.getTag();

            @DrawableRes int icon = R.drawable.ic_place;
            @ColorInt int color = R.color.colorAccentLight;

            //long id = cursor.getLong(cursor.getColumnIndex("_id"));
            String name = cursor.getString(cursor.getColumnIndex("name"));
            int kind = cursor.getInt(cursor.getColumnIndex("kind"));
            float lat = cursor.getFloat(cursor.getColumnIndex("lat"));
            float lon = cursor.getFloat(cursor.getColumnIndex("lon"));

            final GeoPoint coordinates = new GeoPoint(lat, lon);
            double dist = mCoordinates.vincentyDistance(coordinates);
            double bearing = mCoordinates.bearingTo(coordinates);
            String distance = StringFormatter.distanceH(dist) + " " + StringFormatter.angleH(bearing);
            holder.name.setText(name);
            holder.distance.setText(distance);
            holder.viewButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    mMapHolder.setMapLocation(coordinates);
                    //mFragmentHolder.popAll();
                }
            });

            if (Tags.isPlace(kind))
                icon = R.drawable.ic_adjust;
            else if (Tags.isEmergency(kind))
                icon = R.drawable.ic_local_hospital;
            else if (Tags.isAccommodation(kind))
                icon = R.drawable.ic_hotel;
            else if (Tags.isFood(kind))
                icon = R.drawable.ic_local_dining;
            else if (Tags.isAttraction(kind))
                icon = R.drawable.ic_account_balance;
            else if (Tags.isEntertainment(kind))
                icon = R.drawable.ic_local_see;
            else if (Tags.isShopping(kind))
                icon = R.drawable.ic_shopping_cart;
            else if (Tags.isService(kind))
                icon = R.drawable.ic_local_laundry_service;
            else if (Tags.isReligion(kind))
                icon = R.drawable.ic_change_history;
            else if (Tags.isEducation(kind))
                icon = R.drawable.ic_school;
            else if (Tags.isKids(kind))
                icon = R.drawable.ic_child_care;
            else if (Tags.isPets(kind))
                icon = R.drawable.ic_pets;
            else if (Tags.isVehicles(kind))
                icon = R.drawable.ic_directions_car;
            else if (Tags.isTransportation(kind))
                icon = R.drawable.ic_directions_bus;
            else if (Tags.isHikeBike(kind))
                icon = R.drawable.ic_directions_bike;
            else if (Tags.isBuilding(kind))
                icon = R.drawable.ic_location_city;
            else if (Tags.isUrban(kind))
                icon = R.drawable.ic_nature_people;
            else if (Tags.isRoad(kind))
                icon = R.drawable.ic_drag_handle;
            else if (Tags.isBarrier(kind))
                icon = R.drawable.ic_do_not_disturb_on;

            //color = waypoint.style.color;
            holder.icon.setImageResource(icon);
            Drawable background = holder.icon.getBackground().mutate();
            if (background instanceof ShapeDrawable) {
                ((ShapeDrawable) background).getPaint().setColor(color);
            } else if (background instanceof GradientDrawable) {
                ((GradientDrawable) background).setColor(color);
            }
        }
    }

    private static class ItemHolder {
        TextView name;
        TextView distance;
        ImageView icon;
        ImageView viewButton;
    }
}

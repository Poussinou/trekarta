/*
 * Copyright 2018 Andrey Novikov
 *
 * This program is free software: you can redistribute it and/or modify it under the
 * terms of the GNU Lesser General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE. See the GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License along with
 * this program. If not, see <http://www.gnu.org/licenses/>.
 *
 */

package org.oscim.tiling.source.sqlite;

import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import org.oscim.backend.CanvasAdapter;
import org.oscim.backend.canvas.Bitmap;
import org.oscim.core.BoundingBox;
import org.oscim.core.Tile;
import org.oscim.layers.tile.bitmap.BitmapTileLayer;
import org.oscim.tiling.ITileDataSink;
import org.oscim.tiling.ITileDataSource;
import org.oscim.tiling.TileSource;
import org.oscim.tiling.source.ITileDecoder;
import org.oscim.tiling.source.oscimap4.TileDecoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

public class SQLiteTileSource extends TileSource {
    private static final Logger logger = LoggerFactory.getLogger(SQLiteTileSource.class);

    public static final byte[] MAGIC = "SQLite format".getBytes();

    SQLiteDatabase mDatabase;
    private SQLiteOpenHelper mOpenHelper;
    private Class<? extends SQLiteTileDatabase> mTileDatabase;
    BoundingBox mBoundingBox;
    public int sourceZoomMin = 0;

    public SQLiteTileSource() {
    }

    public SQLiteTileSource(SQLiteOpenHelper openHelper) {
        mOpenHelper = openHelper;
    }

    public boolean setMapFile(String filename) {
        setOption("path", filename);

        File file = new File(filename);

        if (!file.exists()) {
            return false;
        } else if (!file.isFile()) {
            return false;
        } else if (!file.canRead()) {
            return false;
        }

        return true;
    }

    @Override
    public ITileDataSource getDataSource() {
        try {
            Constructor con = mTileDatabase.getConstructor(SQLiteTileSource.class, ITileDecoder.class);
            ITileDecoder tileDecoder = "vtm".equals(getOption("format")) ? new TileDecoder() : new BitmapTileDecoder();
            return (ITileDataSource) con.newInstance(this, tileDecoder);
        } catch (NoSuchMethodException | IllegalAccessException | InstantiationException | InvocationTargetException e) {
            e.printStackTrace();
        }
        return null;
    }

    @Override
    public OpenResult open() {
        File file = null;

        if (mOpenHelper != null) {
            mDatabase = mOpenHelper.getReadableDatabase();
            setOption("name", mOpenHelper.getDatabaseName());
        } else {
            if (!options.containsKey("path"))
                return new OpenResult("no map path set");

            file = new File(options.get("path"));

            // check if the path exists and is readable
            if (!file.exists()) {
                return new OpenResult("path does not exist: " + file);
            } else if (!file.isFile()) {
                return new OpenResult("not a path: " + file);
            } else if (!file.canRead()) {
                return new OpenResult("cannot read path: " + file);
            }
            mDatabase = SQLiteDatabase.openDatabase(file.getAbsolutePath(), null, SQLiteDatabase.OPEN_READONLY);
        }

        OpenResult openResult = MBTilesDatabase.initialize(this, mDatabase);

        if (openResult.isSuccess()) {
            mTileDatabase = MBTilesDatabase.class;
        } else {
            openResult = RMapsDatabase.initialize(this, mDatabase);
            if (openResult.isSuccess()) {
                mTileDatabase = RMapsDatabase.class;
            } else {
                close();
                return openResult;
            }
        }

        if (getOption("name") == null && file != null) {
            // Construct name
            // 1. remove extension
            String name = file.getName().toLowerCase();
            int e = name.lastIndexOf(".mbtiles");
            if (e > 0)
                name = name.substring(0, e);
            e = name.lastIndexOf(".sqlitedb");
            if (e > 0)
                name = name.substring(0, e);
            // 2. capitalize first letter
            StringBuilder nameSb = new StringBuilder(name);
            nameSb.setCharAt(0, Character.toUpperCase(nameSb.charAt(0)));
            // 3. append zoom interval
            nameSb.append(" (");
            nameSb.append(String.valueOf(getZoomLevelMin()));
            nameSb.append("-");
            nameSb.append(String.valueOf(getZoomLevelMax()));
            nameSb.append(")");
            setOption("name", nameSb.toString());
        }

        double scaleStart = 1 << sourceZoomMin;
        double scaleEnd = scaleStart * 0.7 + (1 << (sourceZoomMin + 1)) * 0.3;

        BitmapTileLayer.FadeStep fadeStep1 = new BitmapTileLayer.FadeStep(scaleStart, scaleEnd, 0, 1);
        BitmapTileLayer.FadeStep fadeStep2 = new BitmapTileLayer.FadeStep(scaleEnd, 1 << 20, 1, 1);
        setFadeSteps(new BitmapTileLayer.FadeStep[]{fadeStep1, fadeStep2});

        return OpenResult.SUCCESS;
    }

    @Override
    public void close() {
        mDatabase.close();
    }

    void setMinZoom(int minZoom) {
        sourceZoomMin = minZoom;
        mZoomMin = 0;
    }

    void setMaxZoom(int maxZoom) {
        mZoomMax = maxZoom;
    }

    public void setName(String name) {
        setOption("name", name);
    }

    public SQLiteMapInfo getMapInfo() {
        return new SQLiteMapInfo(options.get("name"), mBoundingBox);
    }

    private class BitmapTileDecoder implements ITileDecoder {
        @Override
        public boolean decode(Tile tile, ITileDataSink sink, InputStream is) throws IOException {

            Bitmap bitmap = CanvasAdapter.decodeBitmap(is);
            if (!bitmap.isValid()) {
                logger.warn("invalid bitmap {}", tile);
                return false;
            }
            sink.setTileImage(bitmap);

            return true;
        }
    }

}

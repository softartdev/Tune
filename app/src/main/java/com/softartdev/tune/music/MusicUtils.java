/*
 * Copyright (C) 2008 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.softartdev.tune.music;

import android.graphics.Bitmap;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaControllerCompat;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.TextView;

import com.softartdev.tune.R;

import org.jetbrains.annotations.NotNull;

/*
 * Static methods useful for activities
 */
public class MusicUtils {

    static Bitmap resizeBitmap(Bitmap bitmap, Bitmap ref) {
        int w = ref.getWidth();
        int h = ref.getHeight();
        return Bitmap.createScaledBitmap(bitmap, w, h, false);
    }

    public static void updateNowPlaying(@NotNull AppCompatActivity activity) {
        View nowPlayingView = activity.findViewById(R.id.nowplaying);
        if (nowPlayingView == null) {
            return;
        }
        MediaControllerCompat controller = MediaControllerCompat.getMediaController(activity);
        if (controller != null) {
            MediaMetadataCompat metadata = controller.getMetadata();
            if (metadata != null) {
                TextView title = nowPlayingView.findViewById(R.id.title);
                TextView artist = nowPlayingView.findViewById(R.id.artist);
                title.setText(metadata.getString(MediaMetadataCompat.METADATA_KEY_TITLE));
                String artistName = metadata.getString(MediaMetadataCompat.METADATA_KEY_ARTIST);
                if (MusicProvider.UNKOWN.equals(artistName)) {
                    artistName = activity.getString(R.string.unknown_artist_name);
                }
                artist.setText(artistName);
                nowPlayingView.setVisibility(View.VISIBLE);
/*
                nowPlayingView.setOnClickListener(new View.OnClickListener() {
                    public void onClick(View v) {
                        Context c = v.getContext();
                        c.startActivity(new Intent(c, MediaPlaybackActivity.class));
                    }
                });
*/
                return;
            }
        }
        nowPlayingView.setVisibility(View.GONE);
    }

}

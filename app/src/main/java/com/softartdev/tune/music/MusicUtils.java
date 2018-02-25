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

/*
Static methods useful for activities
 */
public class MusicUtils {

    static Bitmap resizeBitmap(Bitmap bitmap, Bitmap ref) {
        int w = ref.getWidth();
        int h = ref.getHeight();
        return Bitmap.createScaledBitmap(bitmap, w, h, false);
    }
/*
    static void updateNowPlaying(AppCompatActivity a) {
        View nowPlayingView = a.findViewById(R.id.nowplaying);
        if (nowPlayingView == null) {
            return;
        }
        MediaController controller = a.getMediaController();
        if (controller != null) {
            MediaMetadata metadata = controller.getMetadata();
            if (metadata != null) {
                TextView title = (TextView) nowPlayingView.findViewById(R.id.title);
                TextView artist = (TextView) nowPlayingView.findViewById(R.id.artist);
                title.setText(metadata.getString(MediaMetadata.METADATA_KEY_TITLE));
                String artistName = metadata.getString(MediaMetadata.METADATA_KEY_ARTIST);
                if (MusicProvider.UNKOWN.equals(artistName)) {
                    artistName = a.getString(R.string.unknown_artist_name);
                }
                artist.setText(artistName);
                nowPlayingView.setVisibility(View.VISIBLE);
                nowPlayingView.setOnClickListener(new View.OnClickListener() {
                    public void onClick(View v) {
                        Context c = v.getContext();
                        c.startActivity(new Intent(c, MediaPlaybackActivity.class));
                    }
                });
                return;
            }
        }
        nowPlayingView.setVisibility(View.GONE);
    }
*/
}

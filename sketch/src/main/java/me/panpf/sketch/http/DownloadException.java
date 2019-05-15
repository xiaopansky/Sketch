/*
 * Copyright (C) 2019 Peng fei Pan <panpfpanpf@outlook.me>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package me.panpf.sketch.http;

import androidx.annotation.NonNull;

import me.panpf.sketch.request.ErrorCause;

@SuppressWarnings("WeakerAccess")
public class DownloadException extends Exception {
    @NonNull
    private ErrorCause errorCause;

    public DownloadException(@NonNull String message, @NonNull ErrorCause errorCause) {
        super(message);
        this.errorCause = errorCause;
    }

    public DownloadException(@NonNull String message, @NonNull Throwable cause, @NonNull ErrorCause errorCause) {
        super(message, cause);
        this.errorCause = errorCause;
    }

    @NonNull
    public ErrorCause getErrorCause() {
        return errorCause;
    }
}

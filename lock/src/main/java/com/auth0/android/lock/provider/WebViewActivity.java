/*
 * WebViewActivity.java
 *
 * Copyright(c) 2016 Auth0 (http://auth0.com)
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package com.auth0.android.lock.provider;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.Intent;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.drawable.ColorDrawable;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.ViewUtils;
import android.util.Log;
import android.view.View;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.auth0.android.lock.BuildConfig;
import com.auth0.android.lock.R;

public class WebViewActivity extends AppCompatActivity {

    private static final String TAG = WebViewActivity.class.getSimpleName();
    private static final String KEY_REDIRECT_URI = "redirect_uri";
    public static final String CONNECTION_NAME_EXTRA = "serviceName";

    private WebView webView;
    private ProgressBar progressBar;
    private View errorView;
    private TextView errorMessage;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.com_auth0_lock_activity_web_view);
        final ActionBar bar = getSupportActionBar();
        final TextView barTitle = (TextView) findViewById(R.id.com_auth0_lock_title);
        String serviceName = getIntent().getStringExtra(CONNECTION_NAME_EXTRA);
        serviceName = String.format(getString(R.string.com_auth0_lock_webview_header), serviceName);
        TypedArray typedArray = getTheme().obtainStyledAttributes(R.style.Lock_Theme, new int[]{R.styleable.Lock_Theme_Auth0_HeaderBackground, R.styleable.Lock_Theme_Auth0_HeaderTextColor});
        int barColor = typedArray.getColor(R.styleable.Lock_Theme_Auth0_HeaderBackground, ContextCompat.getColor(this, R.color.com_auth0_lock_header_background));
        typedArray.recycle();
        if (bar != null) {
            barTitle.setVisibility(View.GONE);
            findViewById(R.id.com_auth0_lock_title).setVisibility(View.GONE);
            bar.setIcon(android.R.color.transparent);
            bar.setDisplayShowTitleEnabled(false);
            bar.setDisplayUseLogoEnabled(false);
            bar.setDisplayHomeAsUpEnabled(false);
            bar.setDisplayShowCustomEnabled(true);
            bar.setBackgroundDrawable(new ColorDrawable(barColor));
            bar.setTitle(serviceName);
        } else {
            barTitle.setText(serviceName);
        }
        webView = (WebView) findViewById(R.id.com_auth0_lock_webview);
        webView.setVisibility(View.INVISIBLE);
        progressBar = (ProgressBar) findViewById(R.id.com_auth0_lock_progressbar);
        errorView = findViewById(R.id.com_auth0_lock_error_view);
        errorView.setVisibility(View.GONE);
        errorMessage = (TextView) findViewById(R.id.com_auth0_lock_text);
        Button retryButton = (Button) findViewById(R.id.com_auth0_lock_retry);
        retryButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                errorView.setVisibility(View.GONE);
                startUrlLoading();
            }
        });

        startUrlLoading();
    }

    private void startUrlLoading() {
        if (!isNetworkAvailable()) {
            renderLoadError(getString(R.string.com_auth0_lock_network_error));
            return;
        }

        final Intent intent = getIntent();
        final Uri uri = intent.getData();
        final String redirectUrl = uri.getQueryParameter(KEY_REDIRECT_URI);
        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                super.onProgressChanged(view, newProgress);
                if (newProgress > 0) {
                    progressBar.setIndeterminate(false);
                    progressBar.setProgress(newProgress);
                }
            }
        });
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                if (url.startsWith(redirectUrl)) {
                    final Intent intent = new Intent();
                    intent.setData(Uri.parse(url));
                    setResult(RESULT_OK, intent);
                    finish();
                    return true;
                }
                return false;
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                progressBar.setProgress(0);
                progressBar.setIndeterminate(true);
                progressBar.setVisibility(View.GONE);
                final boolean isShowingError = errorView.getVisibility() == View.VISIBLE;
                webView.setVisibility(isShowingError ? View.INVISIBLE : View.VISIBLE);
            }

            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                super.onPageStarted(view, url, favicon);
                progressBar.setProgress(0);
                progressBar.setVisibility(View.VISIBLE);
            }

            @SuppressWarnings("deprecation")
            @Override
            public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
                renderLoadError(description);
                super.onReceivedError(view, errorCode, description, failingUrl);
            }

            @TargetApi(Build.VERSION_CODES.M)
            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                renderLoadError(error.getDescription().toString());
                super.onReceivedError(view, request, error);
            }

        });
        webView.getSettings().setJavaScriptEnabled(true);
        webView.getSettings().setSupportZoom(true);
        webView.getSettings().setBuiltInZoomControls(true);
        webView.loadUrl(uri.toString());
    }

    private void renderLoadError(String description) {
        errorMessage.setText(description);
        webView.setVisibility(View.INVISIBLE);
        errorView.setVisibility(View.VISIBLE);
    }

    private boolean isNetworkAvailable() {
        boolean available = true;
        ConnectivityManager connectivityManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        try {
            NetworkInfo activeNetworkInfo = connectivityManager.getActiveNetworkInfo();
            available = activeNetworkInfo != null && activeNetworkInfo.isConnectedOrConnecting();
        } catch (SecurityException e) {
            Log.w(TAG, "Could not check for Network status. Please, be sure to include the android.permission.ACCESS_NETWORK_STATE permission in the manifest");
        }
        return available;
    }

    @Override
    public void onBackPressed() {
        if (webView.canGoBackOrForward(-1)) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }
}
package org.libreoffice.ui;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;

import org.libreoffice.R;

public class PasswordDialogFragment extends DialogFragment {

    private MainActivity mContext;

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        LayoutInflater inflater = getActivity().getLayoutInflater();

        final View dialogView = inflater.inflate(R.layout.password_dialog, null);

        builder.setView(dialogView)
                .setPositiveButton(R.string.action_pwd_dialog_OK, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        String pwd = ((EditText)dialogView.findViewById(R.id.password)).getText().toString();
                        mContext.savePassword(pwd);
                    }
                })
                .setNegativeButton(R.string.action_pwd_dialog_cancel, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        mContext.savePassword(null);
                    }
                }).setTitle(R.string.action_pwd_dialog_title);

        return builder.create();
    }

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        getDialog().setCanceledOnTouchOutside(false);
        setCancelable(false);
        return super.onCreateView(inflater, container, savedInstanceState);
    }

    public void setLOMainActivity(MainActivity context) {
        mContext = context;
    }
}

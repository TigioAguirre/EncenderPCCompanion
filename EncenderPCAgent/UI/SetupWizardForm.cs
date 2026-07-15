using EncenderPCAgent.Config;
using EncenderPCAgent.Installer;
using EncenderPCAgent.Pairing;
using Microsoft.Extensions.Configuration;

namespace EncenderPCAgent.UI;

/// <summary>
/// Ventana única que reemplaza a la consola negra para instalar y
/// emparejar el agente. Misma paleta que EncenderPCCompanion (fondo
/// #0B1120, tarjetas #17233F, acento #2882EB) para que se sienta parte
/// de la misma app y no una herramienta de desarrollador aparte.
///
/// Todo el contenido vive en <see cref="_content"/>: cada paso del
/// wizard limpia ese panel y dibuja el suyo (más simple que manejar
/// varios UserControl para una ventana tan chica).
/// </summary>
public sealed class SetupWizardForm : Form
{
    // Paleta EncenderPCCompanion.
    private static readonly Color BgColor = ColorTranslator.FromHtml("#0B1120");
    private static readonly Color CardColor = ColorTranslator.FromHtml("#17233F");
    private static readonly Color CardBorderColor = ColorTranslator.FromHtml("#243252");
    private static readonly Color AccentColor = ColorTranslator.FromHtml("#2882EB");
    private static readonly Color AccentHoverColor = ColorTranslator.FromHtml("#3E92F5");
    private static readonly Color TextColor = ColorTranslator.FromHtml("#E7ECF7");
    private static readonly Color MutedTextColor = ColorTranslator.FromHtml("#94A3B8");
    private static readonly Color SuccessColor = ColorTranslator.FromHtml("#3DD68C");
    private static readonly Color ErrorColor = ColorTranslator.FromHtml("#F0596E");

    private readonly Panel _content = new() { Dock = DockStyle.Fill, BackColor = BgColor, Padding = new Padding(36, 28, 36, 28) };
    private readonly Label _footerHint = new()
    {
        Dock = DockStyle.Bottom,
        Height = 28,
        TextAlign = ContentAlignment.MiddleCenter,
        ForeColor = MutedTextColor,
        Font = new Font("Segoe UI", 8.5f),
        Text = "EncenderPC Companion · Agente de esta PC"
    };

    private FirebaseSettings? _firebaseSettings;

    public SetupWizardForm()
    {
        Text = "EncenderPC - Configuración";
        ClientSize = new Size(520, 460);
        FormBorderStyle = FormBorderStyle.FixedDialog;
        MaximizeBox = false;
        MinimizeBox = true;
        StartPosition = FormStartPosition.CenterScreen;
        BackColor = BgColor;
        Font = new Font("Segoe UI", 9.5f);
        Icon = TryLoadIcon();

        Controls.Add(_content);
        Controls.Add(_footerHint);

        ShowWelcomeStep();
    }

    private static Icon? TryLoadIcon()
    {
        try
        {
            var path = Path.Combine(AppContext.BaseDirectory, "app.ico");
            return File.Exists(path) ? new Icon(path) : null;
        }
        catch
        {
            return null;
        }
    }

    // ---------------------------------------------------------------
    // Paso 1: Bienvenida
    // ---------------------------------------------------------------
    private void ShowWelcomeStep()
    {
        _content.Controls.Clear();

        var layout = NewVerticalStack();

        var title = MakeTitle("Configurar EncenderPC Agent");
        var subtitle = MakeSubtitle(
            "Este asistente instala el agente como servicio de Windows y " +
            "empareja esta PC con tu cuenta de EncenderPC Companion. " +
            "No hace falta usar PowerShell ni la consola.");

        var card = MakeCard();
        card.Controls.Add(MakeStepRow("1", "Instalar el servicio de Windows"));
        card.Controls.Add(MakeStepRow("2", "Emparejar esta PC con un código de 6 dígitos"));
        card.Controls.Add(MakeStepRow("3", "Arrancar el agente"));

        var installButton = MakeAccentButton("Instalar y continuar →");
        installButton.Click += async (_, _) => await RunInstallStepAsync();

        layout.Controls.Add(title);
        layout.Controls.Add(subtitle);
        layout.Controls.Add(Spacer(16));
        layout.Controls.Add(card);
        layout.Controls.Add(Spacer(24));
        layout.Controls.Add(installButton);

        _content.Controls.Add(layout);
    }

    private FlowLayoutPanel MakeStepRow(string number, string text)
    {
        var row = new FlowLayoutPanel
        {
            FlowDirection = FlowDirection.LeftToRight,
            AutoSize = true,
            WrapContents = false,
            BackColor = Color.Transparent,
            Margin = new Padding(0, 6, 0, 6)
        };

        var badge = new Label
        {
            Text = number,
            AutoSize = false,
            Size = new Size(22, 22),
            BackColor = AccentColor,
            ForeColor = Color.White,
            TextAlign = ContentAlignment.MiddleCenter,
            Font = new Font("Segoe UI", 8.5f, FontStyle.Bold),
            Margin = new Padding(0, 0, 10, 0)
        };
        MakeRounded(badge, 11);

        var label = new Label
        {
            Text = text,
            AutoSize = true,
            ForeColor = TextColor,
            Font = new Font("Segoe UI", 9.5f),
            Margin = new Padding(0, 2, 0, 0)
        };

        row.Controls.Add(badge);
        row.Controls.Add(label);
        return row;
    }

    // ---------------------------------------------------------------
    // Paso 2: Instalación del servicio (con spinner simple de texto)
    // ---------------------------------------------------------------
    private async Task RunInstallStepAsync()
    {
        ShowBusyStep("Instalando el servicio de Windows...", "Esto tarda unos segundos.");

        // No bloquea la UI: sc.exe corre en el pool de threads.
        var (ok, message) = await Task.Run(() =>
        {
            var success = ServiceInstaller.RegisterService(out var msg);
            return (success, msg);
        });

        if (!ok)
        {
            ShowErrorStep("No se pudo instalar el servicio", message, ShowWelcomeStep);
            return;
        }

        if (DeviceCredentialsStore.Exists())
        {
            ShowAlreadyPairedStep();
        }
        else
        {
            ShowPairingStep();
        }
    }

    // ---------------------------------------------------------------
    // Paso, si ya estaba emparejada de antes
    // ---------------------------------------------------------------
    private void ShowAlreadyPairedStep()
    {
        _content.Controls.Clear();
        var layout = NewVerticalStack();

        layout.Controls.Add(MakeTitle("Esta PC ya estaba emparejada"));
        layout.Controls.Add(MakeSubtitle(
            "El servicio quedó instalado. Podés dejarla como está o volver a " +
            "emparejarla con un código nuevo (por ejemplo, si cambiaste de cuenta)."));
        layout.Controls.Add(Spacer(20));

        var continueButton = MakeAccentButton("Continuar sin cambiar →");
        continueButton.Click += async (_, _) => await RunFinishStepAsync(startService: true);

        var repairButton = MakeSecondaryButton("Volver a emparejar con un código nuevo");
        repairButton.Click += (_, _) => ShowPairingStep();

        layout.Controls.Add(continueButton);
        layout.Controls.Add(Spacer(10));
        layout.Controls.Add(repairButton);

        _content.Controls.Add(layout);
    }

    // ---------------------------------------------------------------
    // Paso 3: Emparejamiento (código de 6 dígitos)
    // ---------------------------------------------------------------
    private void ShowPairingStep()
    {
        _content.Controls.Clear();
        var layout = NewVerticalStack();

        layout.Controls.Add(MakeTitle("Emparejar esta PC"));
        layout.Controls.Add(MakeSubtitle(
            "Abrí la app EncenderPCCompanion, agregá un dispositivo nuevo y " +
            "escribí acá el código de 6 dígitos que te muestre."));
        layout.Controls.Add(Spacer(18));

        var codeBox = new TextBox
        {
            Font = new Font("Consolas", 22f, FontStyle.Bold),
            TextAlign = HorizontalAlignment.Center,
            Width = 220,
            MaxLength = 6,
            BackColor = CardColor,
            ForeColor = TextColor,
            BorderStyle = BorderStyle.FixedSingle,
            Margin = new Padding(0, 0, 0, 6)
        };
        codeBox.KeyPress += (_, e) =>
        {
            // Solo dígitos (y teclas de control como Backspace).
            if (!char.IsDigit(e.KeyChar) && !char.IsControl(e.KeyChar))
            {
                e.Handled = true;
            }
        };

        var codeWrapper = new Panel { AutoSize = true, Margin = new Padding(0) };
        codeWrapper.Controls.Add(codeBox);

        var statusLabel = new Label
        {
            AutoSize = true,
            ForeColor = ErrorColor,
            Font = new Font("Segoe UI", 9f),
            Margin = new Padding(0, 10, 0, 0),
            Visible = false
        };

        var pairButton = MakeAccentButton("Emparejar");
        pairButton.Margin = new Padding(0, 18, 0, 0);
        pairButton.Click += async (_, _) =>
        {
            statusLabel.Visible = false;
            var code = codeBox.Text.Trim();
            if (code.Length != 6)
            {
                statusLabel.Text = "Ingresá los 6 dígitos del código.";
                statusLabel.Visible = true;
                return;
            }

            pairButton.Enabled = false;
            pairButton.Text = "Validando código...";

            var settings = GetFirebaseSettings();
            var result = await PairingService.PairAsync(code, settings);

            if (!result.Success)
            {
                pairButton.Enabled = true;
                pairButton.Text = "Emparejar";
                statusLabel.Text = result.ErrorMessage ?? "No se pudo emparejar. Probá de nuevo.";
                statusLabel.ForeColor = ErrorColor;
                statusLabel.Visible = true;
                return;
            }

            // FIX: PairingService.PairAsync ya arrancó (o reinició) el
            // servicio como parte del emparejamiento. Antes, acá se volvía a
            // llamar RestartService() (stop + start) por segunda vez: si el
            // primer arranque todavía estaba en curso (extrayendo el .exe
            // self-contained o siendo escaneado por el antivirus), este
            // segundo "sc stop" lo cortaba a mitad de camino, y el agente
            // quedaba sin arrancar hasta el próximo reinicio de Windows.
            // Ahora solo esperamos/confirmamos, sin volver a tocar el
            // servicio.
            await RunFinishStepAsync(startService: false);
        };

        layout.Controls.Add(codeWrapper);
        layout.Controls.Add(statusLabel);
        layout.Controls.Add(pairButton);

        _content.Controls.Add(layout);
        codeBox.Focus();
    }

    // ---------------------------------------------------------------
    // Paso final: arrancar/reiniciar el servicio
    // ---------------------------------------------------------------
    /// <param name="startService">
    /// true: todavía no arrancamos el servicio en este flujo (por ejemplo,
    /// "Continuar sin cambiar" después de solo instalar/registrar), así que
    /// hay que pedirle que arranque.
    /// false: el servicio ya se arrancó/reinició un paso antes (dentro de
    /// <see cref="PairingService.PairAsync"/> al emparejar), así que acá
    /// solo esperamos la confirmación en vez de volver a pararlo y
    /// levantarlo (eso era lo que antes interrumpía el primer arranque).
    /// </param>
    private async Task RunFinishStepAsync(bool startService)
    {
        ShowBusyStep("Iniciando el agente...", "Ya casi termina.");

        var running = await Task.Run(() =>
        {
            if (startService)
            {
                ServiceInstaller.RestartService();
                return ServiceInstaller.IsServiceRunning();
            }

            // Ya se pidió el arranque antes: solo confirmamos, dándole
            // margen extra por si la extracción del .exe / el escaneo del
            // antivirus todavía no terminó.
            return ServiceInstaller.WaitUntilRunning();
        });

        ShowFinishedStep(running);
    }

    // ---------------------------------------------------------------
    // Pantalla final
    // ---------------------------------------------------------------
    private void ShowFinishedStep(bool running)
    {
        _content.Controls.Clear();
        var layout = NewVerticalStack();

        if (running)
        {
            layout.Controls.Add(MakeTitle("✔ Listo", SuccessColor));
            layout.Controls.Add(MakeSubtitle(
                "Esta PC ya está reportando su estado a EncenderPC Companion. " +
                "Podés cerrar esta ventana: el agente sigue funcionando solo, " +
                "incluso después de reiniciar Windows."));
        }
        else
        {
            layout.Controls.Add(MakeTitle("Quedó instalado, con un detalle", MutedTextColor));
            layout.Controls.Add(MakeSubtitle(
                "El servicio se instaló y esta PC quedó emparejada, pero no se " +
                "pudo confirmar que el servicio arrancó. Revisá 'Servicios' de " +
                "Windows (busca 'EncenderPCAgent') o reiniciá la PC."));
        }

        layout.Controls.Add(Spacer(20));
        var closeButton = MakeAccentButton("Cerrar");
        closeButton.Click += (_, _) => Close();
        layout.Controls.Add(closeButton);

        _content.Controls.Add(layout);
    }

    // ---------------------------------------------------------------
    // Estados genéricos: ocupado / error
    // ---------------------------------------------------------------
    private void ShowBusyStep(string title, string subtitle)
    {
        _content.Controls.Clear();
        var layout = NewVerticalStack();
        layout.Anchor = AnchorStyles.None;

        var spinner = new Label
        {
            Text = "⟳",
            AutoSize = true,
            Font = new Font("Segoe UI", 28f),
            ForeColor = AccentColor,
            Margin = new Padding(0, 40, 0, 12)
        };
        layout.Controls.Add(spinner);
        layout.Controls.Add(MakeTitle(title));
        layout.Controls.Add(MakeSubtitle(subtitle));

        _content.Controls.Add(layout);
        Application.DoEvents();
    }

    private void ShowErrorStep(string title, string details, Action onRetry)
    {
        _content.Controls.Clear();
        var layout = NewVerticalStack();

        layout.Controls.Add(MakeTitle("✖ " + title, ErrorColor));
        layout.Controls.Add(MakeSubtitle(details));
        layout.Controls.Add(Spacer(20));

        var retryButton = MakeAccentButton("Volver a intentar");
        retryButton.Click += (_, _) => onRetry();
        layout.Controls.Add(retryButton);

        _content.Controls.Add(layout);
    }

    // ---------------------------------------------------------------
    // Config
    // ---------------------------------------------------------------
    private FirebaseSettings GetFirebaseSettings()
    {
        if (_firebaseSettings is not null) return _firebaseSettings;

        var config = new ConfigurationBuilder()
            .SetBasePath(AppContext.BaseDirectory)
            .AddJsonFile("appsettings.json", optional: false)
            .Build();

        _firebaseSettings = new FirebaseSettings();
        config.GetSection("Firebase").Bind(_firebaseSettings);
        return _firebaseSettings;
    }

    // ---------------------------------------------------------------
    // Helpers visuales
    // ---------------------------------------------------------------
    private static FlowLayoutPanel NewVerticalStack() => new()
    {
        FlowDirection = FlowDirection.TopDown,
        Dock = DockStyle.Fill,
        WrapContents = false,
        AutoSize = false,
        BackColor = Color.Transparent
    };

    private static Label MakeTitle(string text, Color? color = null) => new()
    {
        Text = text,
        AutoSize = true,
        ForeColor = color ?? TextColor,
        Font = new Font("Segoe UI", 16f, FontStyle.Bold),
        Margin = new Padding(0, 0, 0, 8),
        MaximumSize = new Size(440, 0)
    };

    private static Label MakeSubtitle(string text) => new()
    {
        Text = text,
        AutoSize = true,
        ForeColor = MutedTextColor,
        Font = new Font("Segoe UI", 9.5f),
        MaximumSize = new Size(440, 0),
        Margin = new Padding(0, 0, 0, 4)
    };

    private static Panel Spacer(int height) => new() { Height = height, Width = 1, Margin = new Padding(0) };

    private static Panel MakeCard()
    {
        var card = new Panel
        {
            AutoSize = true,
            BackColor = CardColor,
            Padding = new Padding(18, 14, 18, 10),
            Margin = new Padding(0, 0, 0, 0),
            MinimumSize = new Size(440, 0)
        };
        card.Paint += (_, e) =>
        {
            using var pen = new Pen(CardBorderColor);
            e.Graphics.DrawRectangle(pen, 0, 0, card.Width - 1, card.Height - 1);
        };
        return card;
    }

    private static Button MakeAccentButton(string text)
    {
        var button = new Button
        {
            Text = text,
            Width = 440,
            Height = 42,
            FlatStyle = FlatStyle.Flat,
            BackColor = AccentColor,
            ForeColor = Color.White,
            Font = new Font("Segoe UI", 10f, FontStyle.Bold),
            Cursor = Cursors.Hand,
            Margin = new Padding(0)
        };
        button.FlatAppearance.BorderSize = 0;
        button.FlatAppearance.MouseOverBackColor = AccentHoverColor;
        button.FlatAppearance.MouseDownBackColor = AccentColor;
        return button;
    }

    private static Button MakeSecondaryButton(string text)
    {
        var button = new Button
        {
            Text = text,
            Width = 440,
            Height = 38,
            FlatStyle = FlatStyle.Flat,
            BackColor = CardColor,
            ForeColor = MutedTextColor,
            Font = new Font("Segoe UI", 9f),
            Cursor = Cursors.Hand,
            Margin = new Padding(0)
        };
        button.FlatAppearance.BorderColor = CardBorderColor;
        button.FlatAppearance.BorderSize = 1;
        button.FlatAppearance.MouseOverBackColor = ColorTranslator.FromHtml("#1D2B4D");
        return button;
    }

    /// <summary>Redondea un control chico (usado para el numerito de cada paso).</summary>
    private static void MakeRounded(Control control, int radius)
    {
        control.Region = new Region(RoundedRect(new Rectangle(0, 0, control.Width, control.Height), radius));
    }

    private static System.Drawing.Drawing2D.GraphicsPath RoundedRect(Rectangle bounds, int radius)
    {
        var path = new System.Drawing.Drawing2D.GraphicsPath();
        var d = radius * 2;
        path.AddArc(bounds.X, bounds.Y, d, d, 180, 90);
        path.AddArc(bounds.Right - d, bounds.Y, d, d, 270, 90);
        path.AddArc(bounds.Right - d, bounds.Bottom - d, d, d, 0, 90);
        path.AddArc(bounds.X, bounds.Bottom - d, d, d, 90, 90);
        path.CloseFigure();
        return path;
    }
}

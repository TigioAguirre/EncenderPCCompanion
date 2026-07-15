using EncenderPCAgent.Installer;

namespace EncenderPCAgent.UI;

/// <summary>
/// Arranca la ventana gráfica del instalador cuando el usuario hace doble
/// click en EncenderPCAgent.exe desde el Explorador de Windows.
///
/// El pedido de permisos de Administrador pasa ANTES de crear cualquier
/// ventana: si no relanzamos elevado acá, "sc create" (registrar el
/// servicio) falla más adelante con un error críptico. Mejor pedirlo ya,
/// con el cuadro de UAC nativo de Windows, apenas se abre el programa.
/// </summary>
public static class SetupWizardApp
{
    public static int Run()
    {
        if (!ServiceInstaller.IsAdministrator())
        {
            var relaunched = ServiceInstaller.RelaunchElevated(Array.Empty<string>());
            if (!relaunched)
            {
                MessageBox.Show(
                    "EncenderPC Agent necesita permisos de Administrador para instalarse " +
                    "como servicio de Windows.\n\nVolvé a abrir el programa y aceptá el " +
                    "cuadro de permisos de Windows.",
                    "Permisos necesarios",
                    MessageBoxButtons.OK,
                    MessageBoxIcon.Warning);
                return 1;
            }

            // Esta instancia (sin permisos) ya cumplió su función lanzando
            // la elevada; la elevada sigue sola desde SetupWizardApp.Run()
            // otra vez, esta vez con IsAdministrator() == true.
            return 0;
        }

        ApplicationConfiguration.Initialize();
        Application.Run(new SetupWizardForm());
        return 0;
    }
}
